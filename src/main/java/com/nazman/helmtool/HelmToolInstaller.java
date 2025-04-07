package com.nazman.helmtool;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class HelmToolInstaller extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public HelmToolInstaller(String id) {
        super(id); // только один аргумент
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) {
        log.getLogger().println("Installing Helm...");
        return null; // Заменить на правильную реализацию или просто убрать переопределение
    }

    @Extension
    public static final class DescriptorImpl extends ToolInstallerDescriptor<HelmToolInstaller> {
        @Override
        public String getDisplayName() {
            return "Install Helm from URL";
        }
    }
}
