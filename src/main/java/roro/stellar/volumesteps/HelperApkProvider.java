package roro.stellar.volumesteps;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Exposes one bundled, verified APK as a read-only installer URI. */
public final class HelperApkProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://roro.stellar.volumesteps.helper_apk/volume-helper.apk");
    static synchronized File prepare(Context context) throws IOException {
        File file = new File(context.getCacheDir(), "volume-helper.apk");
        try (var input = context.getAssets().open("volume-helper.apk");
             var output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(file.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
        try {
            PackageInfo own = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (archive == null || !CompatibilityVolumeBridge.PACKAGE.equals(archive.packageName)
                    || archive.signingInfo == null || own.signingInfo == null
                    || !Arrays.equals(own.signingInfo.getApkContentsSigners(),
                            archive.signingInfo.getApkContentsSigners()))
                throw new IOException("Bundled helper identity mismatch");
        } catch (PackageManager.NameNotFoundException exception) { throw new IOException(exception); }
        return file;
    }
    @Override public boolean onCreate() { return true; }
    private void check(Uri uri) {
        if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown helper URI");
    }
    @Override public String getType(Uri uri) { check(uri); return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        check(uri);
        if (!"r".equals(mode)) throw new java.io.FileNotFoundException("Read only");
        File file = new File(getContext().getCacheDir(), "volume-helper.apk");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String sort) {
        check(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) row.add(OpenableColumns.DISPLAY_NAME.equals(column)
                ? "volume-helper.apk" : OpenableColumns.SIZE.equals(column)
                ? new File(getContext().getCacheDir(), "volume-helper.apk").length() : null);
        return cursor;
    }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
