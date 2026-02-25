package com.nazman.helmtool;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import org.kohsuke.stapler.DataBoundConstructor;

public class HelmToolInstaller extends ToolInstaller {

    private final String downloadUrl;

    @DataBoundConstructor
    public HelmToolInstaller(String downloadUrl) {
        super(null); // no label: installer applies to all nodes including the controller
        this.downloadUrl = downloadUrl;
    }

    /**
     * Gets the download URL for Helm.
     *
     * @return The download URL for Helm
     */
    public String getDownloadUrl() {
        return downloadUrl;
    }

    @Override
    public boolean appliesTo(Node node) {
        return true; // apply on all nodes including the Jenkins controller
    }

    /** Connect timeout for Helm download (milliseconds). */
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000;
    /** Read timeout for Helm download (milliseconds). */
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 300_000;

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log)
            throws IOException, InterruptedException {
        log.getLogger().println("Installing Helm...");

        // Use installation directory from tool settings (Helm Home)
        FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            throw new IOException("Node root path is not available.");
        }
        String home = Util.fixEmptyAndTrim(tool.getHome());
        if (home == null) {
            home = tool.getName();
        }
        FilePath installationDir =
                isAbsolutePath(home) ? new FilePath(rootPath.getChannel(), home) : new FilePath(rootPath, home);
        installationDir.mkdirs();

        // Download the archive from URL (with timeouts to avoid hanging in containers)
        FilePath downloaded = installationDir.child(".download/helm.tar.gz");
        FilePath downloadParent = downloaded.getParent();
        if (downloadParent != null) {
            downloadParent.mkdirs();
        }
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            throw new IOException("Download URL is null or empty");
        }
        log.getLogger().println("Downloading Helm from " + downloadUrl + " ...");
        try {
            URL url = new java.net.URI(downloadUrl).toURL();
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
            try (InputStream in = conn.getInputStream()) {
                downloaded.copyFrom(in);
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid download URL: " + downloadUrl, e);
        }

        // Extract to a temp dir; archive has one top-level dir (e.g. linux-amd64) with helm inside
        log.getLogger().println("Extracting archive...");
        FilePath extractDir = installationDir.child(".extract");
        extractDir.mkdirs();
        extractDir.untarFrom(downloaded.read(), FilePath.TarCompression.GZIP);
        downloaded.delete();

        // Find helm binary (helm or helm.exe) in extracted content
        FilePath helmBinary = findHelmBinary(extractDir);
        if (helmBinary == null) {
            extractDir.deleteRecursive();
            throw new IOException(
                    "Helm binary not found in archive. Expected 'helm' or 'helm.exe' under a top-level directory.");
        }

        FilePath targetBinary = installationDir.child(helmBinary.getName());
        helmBinary.copyTo(targetBinary);
        extractDir.deleteRecursive();

        if (!"helm.exe".equals(helmBinary.getName())) {
            targetBinary.chmod(0755);
        }

        log.getLogger().println("Helm installed successfully at " + targetBinary.getRemote());
        return installationDir;
    }

    private static boolean isAbsolutePath(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.startsWith("/")) return true;
        if (path.length() >= 2 && path.charAt(1) == ':') return true;
        return false;
    }

    /** Finds helm or helm.exe in the extracted directory (may be in a single subdir). */
    private static FilePath findHelmBinary(FilePath dir) throws IOException, InterruptedException {
        for (FilePath child : dir.list()) {
            String name = child.getName();
            if ("helm".equals(name) || "helm.exe".equals(name)) {
                return child;
            }
            if (child.isDirectory()) {
                FilePath inSubdir = findHelmBinary(child);
                if (inSubdir != null) return inSubdir;
            }
        }
        return null;
    }

    @Extension
    public static final class DescriptorImpl extends ToolInstallerDescriptor<HelmToolInstaller> {
        @Override
        public String getDisplayName() {
            return "Install Helm from URL";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == HelmToolInstallation.class;
        }
    }
}
