package com.nazman.helmtool;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class HelmTool extends Builder implements SimpleBuildStep {

    private final String releaseName;
    private final String chartPath;
    private final String helmInstallation;
    private final String additionalArgs;
    private List<Repository> repositories = new ArrayList<>();

    @DataBoundConstructor
    public HelmTool(String releaseName, String chartPath, String helmInstallation, String additionalArgs) {
        this.releaseName = releaseName;
        this.chartPath = chartPath;
        this.helmInstallation = helmInstallation;
        this.additionalArgs = additionalArgs;
    }

    public String getReleaseName() {
        return releaseName;
    }

    public String getChartPath() {
        return chartPath;
    }

    public String getHelmInstallation() {
        return helmInstallation;
    }

    public String getAdditionalArgs() {
        return additionalArgs;
    }

    @DataBoundSetter
    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories;
    }

    public List<Repository> getRepositories() {
        return repositories;
    }

    // Вложенный класс для представления репозитория
    public static class Repository implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String url;

        @DataBoundConstructor
        public Repository(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        @DataBoundSetter
        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        @DataBoundSetter
        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isValidUrl() {
            if (url == null || url.trim().isEmpty()) {
                return false;
            }
            try {
                URI.create(url);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        listener.getLogger().println("Release Name: " + releaseName);
        listener.getLogger().println("Chart Path: " + chartPath);
        listener.getLogger().println("Repositories: " + (repositories != null ? repositories.size() : "null"));

        // Use default (first available) installation when not specified or empty
        String installationName =
                (helmInstallation != null && !helmInstallation.trim().isEmpty()) ? helmInstallation.trim() : null;
        if (installationName == null) {
            HelmToolInstallation[] available = getDescriptor().getInstallations();
            if (available != null && available.length > 0) {
                installationName = available[0].getName();
                listener.getLogger().println("Using default Helm installation: " + installationName);
            }
        } else {
            listener.getLogger().println("Running Helm command with installation: " + installationName);
        }

        HelmToolInstallation helmTool =
                installationName != null ? getDescriptor().getInstallation(installationName) : null;
        if (helmTool == null) {
            // List available installations for better error message
            HelmToolInstallation[] availableInstallations = getDescriptor().getInstallations();
            String requestedName =
                    (helmInstallation != null && !helmInstallation.trim().isEmpty())
                            ? helmInstallation.trim()
                            : "(default/empty)";
            StringBuilder errorMsg = new StringBuilder("Helm installation not found: " + requestedName);
            if (availableInstallations != null && availableInstallations.length > 0) {
                errorMsg.append(". Available installations: ");
                for (int i = 0; i < availableInstallations.length; i++) {
                    if (i > 0) errorMsg.append(", ");
                    errorMsg.append(availableInstallations[i].getName());
                }
            } else {
                errorMsg.append(
                        ". No Helm installations are configured. Please configure at least one Helm installation in Jenkins Global Tool Configuration.");
            }
            throw new IOException(errorMsg.toString());
        }

        // Get the node and ensure Helm is installed
        Node node = workspace.toComputer().getNode();
        if (node == null) {
            throw new IOException("Unable to determine the build node. The node may be offline or disconnected.");
        }

        // Translate for node - this triggers automatic installation if needed
        EnvVars env = run.getEnvironment(listener);
        helmTool = (HelmToolInstallation) helmTool.translate(node, env, listener);

        String helmPath = helmTool.getHelmBinaryPath(node, listener);
        listener.getLogger().println("Using Helm binary at: " + helmPath);

        // Добавляем репозитории, если они указаны
        if (repositories != null && !repositories.isEmpty()) {
            for (Repository repo : repositories) {
                // Validate URL before using it
                if (!repo.isValidUrl()) {
                    listener.getLogger()
                            .println("Warning: Invalid URL for repository " + repo.getName() + ": " + repo.getUrl());
                    continue;
                }

                String repoAddCommand = String.format("%s repo add %s %s", helmPath, repo.getName(), repo.getUrl());
                listener.getLogger().println("Adding repository: " + repoAddCommand);
                int repoAddExitCode = launcher.launch()
                        .cmdAsSingleString(repoAddCommand)
                        .stdout(listener.getLogger())
                        .stderr(listener.getLogger())
                        .pwd(workspace)
                        .start()
                        .join();
                if (repoAddExitCode != 0) {
                    listener.getLogger()
                            .println("Failed to add repository " + repo.getName() + " with exit code: "
                                    + repoAddExitCode);
                }
            }

            // Обновляем индекс репозиториев
            listener.getLogger().println("Updating Helm repositories...");
            int repoUpdateExitCode = launcher.launch()
                    .cmdAsSingleString(helmPath + " repo update")
                    .stdout(listener.getLogger())
                    .stderr(listener.getLogger())
                    .pwd(workspace)
                    .start()
                    .join();
            if (repoUpdateExitCode != 0) {
                listener.getLogger().println("Failed to update repositories with exit code: " + repoUpdateExitCode);
            }
        }

        String helmCommand = String.format(
                "%s install %s %s %s", helmPath, releaseName, chartPath, additionalArgs != null ? additionalArgs : "");

        listener.getLogger().println("Executing: " + helmCommand);

        int exitCode = launcher.launch()
                .cmdAsSingleString(helmCommand)
                .stdout(listener.getLogger())
                .stderr(listener.getLogger())
                .pwd(workspace)
                .join();

        if (exitCode != 0) {
            throw new IOException("Helm command failed with exit code " + exitCode);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("helm")
    @Extension
    public static final class DescriptorImpl extends hudson.tasks.BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Deploy Helm chart";
        }

        public ListBoxModel doFillHelmInstallationItems() {
            ListBoxModel items = new ListBoxModel();
            for (HelmToolInstallation installation : getInstallations()) {
                items.add(installation.getName(), installation.getName());
            }
            return items;
        }

        public HelmToolInstallation getInstallation(String name) {
            for (HelmToolInstallation inst : getInstallations()) {
                if (inst.getName().equals(name)) {
                    return inst;
                }
            }
            return null;
        }

        public HelmToolInstallation[] getInstallations() {
            return jenkins.model.Jenkins.get()
                    .getDescriptorByType(HelmToolInstallation.DescriptorImpl.class)
                    .getInstallations();
        }

        // Add method to create a new Repository instance
        public Repository newRepository(String name, String url) {
            return new Repository(name, url);
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("URL cannot be empty");
            }
            try {
                URI.create(value);
                return FormValidation.ok();
            } catch (IllegalArgumentException e) {
                return FormValidation.error("Invalid URL format: " + e.getMessage());
            }
        }
    }
}
