package com.nazman.helmtool;

import hudson.Extension;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

public class HelmToolInstallation extends ToolInstallation {

    @DataBoundConstructor
    public HelmToolInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<HelmToolInstallation> {
        @Override
        public String getDisplayName() {
            return "Helm Installation";
        }
    }
}
