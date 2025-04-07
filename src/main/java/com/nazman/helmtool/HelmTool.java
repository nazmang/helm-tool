package com.nazman.helmtool;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

public class HelmTool extends Builder implements SimpleBuildStep {

    private final String releaseName;
    private final String chartPath;
    private final String helmInstallation;

    @DataBoundConstructor
    public HelmTool(String releaseName, String chartPath, String helmInstallation) {
        this.releaseName = releaseName;
        this.chartPath = chartPath;
        this.helmInstallation = helmInstallation;
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

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        listener.getLogger().println("Running Helm command with installation: " + helmInstallation);

        HelmToolInstallation helmTool = getDescriptor().getInstallation(helmInstallation);
        String helmPath = helmTool != null && helmTool.getHome() != null ? helmTool.getHome() : "helm";

        String helmCommand = String.format("%s install %s %s", helmPath, releaseName, chartPath);

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

    @Symbol("helmTool")
    @Extension
    public static final class DescriptorImpl extends hudson.tasks.BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class type) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Deploy Helm chart";
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
    }
}
