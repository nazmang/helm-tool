package com.nazman.helmtool;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.PersistentDescriptor;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class HelmToolInstallation extends ToolInstallation implements NodeSpecific<HelmToolInstallation> {

    @DataBoundConstructor
    public HelmToolInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    @Override
    public HelmToolInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        String home = translateFor(node, log);
        return new HelmToolInstallation(getName(), home, getProperties().toList());
    }

    @Override
    public String getHome() {
        String home = super.getHome();
        if (home == null || home.trim().isEmpty()) {
            // Default to the name if home is not set
            return getName();
        }
        return home;
    }

    /**
     * Gets the path to the Helm binary
     *
     * @param node The node to get the path for
     * @param log  The task listener for logging
     * @return The path to the Helm binary
     * @throws IOException          if there is an error
     * @throws InterruptedException if the operation is interrupted
     */
    public String getHelmBinaryPath(Node node, TaskListener log) throws IOException, InterruptedException {
        // Get the installation directory
        FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            throw new IOException("Node root path is not available. The node may be offline or disconnected.");
        }
        String home = getHome();
        if (home == null || home.trim().isEmpty()) {
            throw new IOException("Helm installation home path is not configured.");
        }
        // Home from automatic installation is absolute; configured home may be relative to node root
        FilePath installationDir =
                isAbsolutePath(home) ? new FilePath(rootPath.getChannel(), home) : new FilePath(rootPath, home);
        FilePath helmBinary = installationDir.child("helm");

        if (!helmBinary.exists()) {
            throw new IOException("Helm binary not found at " + helmBinary.getRemote()
                    + ". Please ensure Helm is installed at the specified location.");
        }

        return helmBinary.getRemote();
    }

    private static boolean isAbsolutePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.startsWith("/")) {
            return true;
        }
        if (path.length() >= 2 && path.charAt(1) == ':') {
            return true; // Windows drive letter
        }
        return false;
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<HelmToolInstallation> implements PersistentDescriptor {

        @Override
        public String getDisplayName() {
            return "Helm";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws Descriptor.FormException {
            super.configure(req, json);
            save();
            return true;
        }

        public FormValidation doCheckHome(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Helm home is required");
            }
            return FormValidation.ok();
        }
    }
}
