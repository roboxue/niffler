package com.example;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CompressUtils {

    public static void decompressTarGz(File in, File out) throws IOException {
        try (TarArchiveInputStream fin = new TarArchiveInputStream(
                new GzipCompressorInputStream(new FileInputStream(in)))) {
            unarchive(fin, out);
        }
    }

    public static void decompressTarBz(File in, File out) throws IOException {
        try (TarArchiveInputStream fin = new TarArchiveInputStream(
                new BZip2CompressorInputStream(new FileInputStream(in)))) {
            unarchive(fin, out);
        }
    }

    public static void decompressZip(File in, File out) throws IOException {
        try (ZipArchiveInputStream fin = new ZipArchiveInputStream(new FileInputStream(in))) {
            unarchive(fin, out);
        }
    }

    private static void unarchive(ArchiveInputStream fin, File out) throws IOException {
        ArchiveEntry entry;
        while ((entry = fin.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            File curfile = new File(out, entry.getName());
            File parent = curfile.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            OutputStream fout = new FileOutputStream(curfile);
            IOUtils.copy(fin, fout);
            fout.close();
        }
    }
}
