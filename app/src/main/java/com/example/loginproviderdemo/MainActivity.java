package com.example.loginproviderdemo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final int SELECT_FILE = 1;
    public static final int CHECK_ALL_PERMISSION_FIRST_RUN = 2;
    public static final String SELECT_IMAGE_TITLE = "Select Image";
    public static final String CONTENT_URI_PUBLIC_DOWNLOADS = "content://downloads/public_downloads";
    public static final String KEY_CONTENT = "content";
    public static final String KEY_FILE = "file";
    public static final String KEY_URI_EXTERNAL_STORAGE_DOCUMENTS = "com.android.externalstorage.documents";
    public static final String KEY_URI_DOWNLOADS_DOCUMENTS = "com.android.providers.downloads.documents";
    public static final String KEY_URI_MEDIA_DOCUMENTS = "com.android.providers.media.documents";
    public static final String PREF_KEY_PRIMARY = "primary";
    public static final String FIELD_IMAGE = "image";
    public static final String FIELD_VIDEO = "video";
    public static final String FIELD_AUDIO = "audio";
    public static final String DATA = "_data";
    public static final String ID = "_id=?";
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String FIELD_HTTP = "http:";
    public static final String FIELD_HTTPS = "https:";
    private ProgressDialog progressDialog;
    private ImageView beforeUploading;
    private ImageView afterUploading;
    private Button uploadButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setMessage("Uploading image...");
        progressDialog.setCancelable(false);

        beforeUploading = (ImageView) findViewById(R.id.imageView);
        afterUploading = (ImageView) findViewById(R.id.imageView2);
        uploadButton = (Button) findViewById(R.id.uploadBtn);

        uploadButton.setOnClickListener(v -> galleryIntent());
        checkPermissionForFirstRun();
    }

    private void checkPermissionForFirstRun() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> requestList = new ArrayList<>();
            checkPermissionForReadWriteExternalStorage(requestList);
            if (!requestList.isEmpty()) {
                String[] requestArr = new String[requestList.size()];
                requestArr = requestList.toArray(requestArr);
                ActivityCompat.requestPermissions(this, requestArr, CHECK_ALL_PERMISSION_FIRST_RUN);
            } else {
                // nothing to do for now
            }
        } else {
            // nothing to do for now
        }
    }

    private void checkPermissionForReadWriteExternalStorage(ArrayList<String> requestList) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CHECK_ALL_PERMISSION_FIRST_RUN) {
            checkForAllPermission(grantResults);
        }
    }

    /**
     * Check all permissions is granted or not when user first install app.
     *
     * @param grantResults
     */
    private void checkForAllPermission(int[] grantResults) {
        boolean isGratedFully = true;
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                isGratedFully = false;
                break;
            }
        }
        if (isGratedFully) {
            // Nothing to do as permission is granted
        } else {
            Toast.makeText(this, "Permission is required!", Toast.LENGTH_SHORT).show();
            checkPermissionForFirstRun();
        }
    }

    private void galleryIntent() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(Intent.createChooser(intent, SELECT_IMAGE_TITLE), SELECT_FILE);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SELECT_FILE) {
                Uri uri;
                uri = onSelectFromGalleryResult(data);
                if (uri != null) {
                    String url = getPath(MainActivity.this, uri);
                    Log.d(TAG, "URI Gallery::  " + uri);
                    Glide.with(MainActivity.this).load(uri).into(beforeUploading);
                    progressDialog.show();
                    finishActivity(url);

                } else {
                    Toast.makeText(MainActivity.this, "There is an error in selecting image...", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void finishActivity(String userImage) {
        new UploadImageTask(userImage).execute();
    }

    private class UploadImageTask extends AsyncTask<Void, String, String> {
        final ConditionVariable conditionVariable = new ConditionVariable(false);
        File file;
        private String userImage;

        UploadImageTask(String userimage) {
            this.userImage = userimage;
        }

        @Override
        protected String doInBackground(Void... params) {
           file = new File(userImage);

            String uniqueId = UUID.randomUUID().toString();
            final String halfUrl = "user/" + uniqueId + ".jpg";
            CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                    MainActivity.this,
                    getString(R.string.amazon_pool_id),
                    Regions.fromName(getString(R.string.amazon_region)));
            ClientConfiguration configuration = new ClientConfiguration();

            // =============== THIS BLOCK OF CODE CREATE THE ISSUE ================
            Map<String, String> logins = new HashMap<>();
            logins.put(getString(R.string.auth0_domain), "Bearer " + getString(R.string.auth0_token));
            Log.d(TAG, logins.toString());
            credentialsProvider.setLogins(logins); // if we comment this line, it will work
            //=====================================================================

            // Reference: https://stackoverflow.com/questions/54513921/logins-dont-match-please-include-at-least-one-valid-login-for-this-identity-or
            /**
             * Logins don't match. Please include at least one valid login for this
             * identity or identity pool.
             * (Service: AmazonCognitoIdentity; Status Code: 400;
             * Error Code: NotAuthorizedException; Request ID: 9c14eabe-604f-11e9-beca-ad4a2d9eb55d)
             */

            configuration.setMaxErrorRetry(3);
            configuration.setConnectionTimeout(501000);
            configuration.setSocketTimeout(501000);
            configuration.setProtocol(Protocol.HTTP);
            AmazonS3Client s3Client = new AmazonS3Client(credentialsProvider, configuration);
            TransferUtility transferUtility = new TransferUtility(s3Client, MainActivity.this);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            TransferObserver observer = transferUtility.upload(getString(R.string.amazon_bucket_name), halfUrl,
                    file, objectMetadata);
            observer.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (state == TransferState.COMPLETED) {
                        FutureTarget<File> futureTarget = Glide.with(getApplicationContext())
                                .load(getString(R.string.amazon_url) + getString(R.string.amazon_bucket_name) + "/" + halfUrl)
                                .downloadOnly(1024, 1024);
                        new Thread(() -> {
                            try {
                                File file = futureTarget.get();
                                String path = file.getAbsolutePath();
                                Log.d(TAG, "----------------- cache Image: " + path);
                            } catch (InterruptedException | ExecutionException e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                            conditionVariable.open();
                        }).start();
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    Log.d(TAG, "--- is Uploading");
                }

                @Override
                public void onError(int id, Exception ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    conditionVariable.open();
                }
            });
            conditionVariable.block();
            return halfUrl;
        }

        @Override
        protected void onPostExecute(String halfURL) {
            super.onPostExecute(halfURL);
            progressDialog.dismiss();
            String finalUrl = null;
            if (halfURL != null && !halfURL.isEmpty()) {
                if (halfURL.startsWith(FIELD_HTTP) || halfURL.startsWith(FIELD_HTTPS)) {
                    // do nothing here
                } else {
                    finalUrl = getString(R.string.amazon_url) + getString(R.string.amazon_bucket_name) + "/" + halfURL;
                    Log.e("AMAZON URL:::", "url is:" + finalUrl);
                }
                Glide.with(getApplicationContext())
                        .load(finalUrl)
                        .transform(new GlideCircleTransform(getApplicationContext()))
                        .override(100, 200)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .listener(new RequestListener<String, GlideDrawable>() {
                            @Override
                            public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(afterUploading);

            } else {
                Toast.makeText(MainActivity.this, "There are some error in uploading image...", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private Uri onSelectFromGalleryResult(Intent data) {
        return data.getData();
    }

    /**
     * @param context context of the screen
     * @param uri     uri to get path of it
     * @return response String value
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String getPath(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if (PREF_KEY_PRIMARY.equalsIgnoreCase(type))
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(Uri.parse(CONTENT_URI_PUBLIC_DOWNLOADS), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
                return handleMediaDocument(uri, context);
            }
        } else if (KEY_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        } else if (KEY_FILE.equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private static String handleMediaDocument(Uri uri, Context context) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];
        Uri contentUri = null;
        if (FIELD_IMAGE.equals(type)) {
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if (FIELD_VIDEO.equals(type)) {
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else if (FIELD_AUDIO.equals(type)) {
            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
        final String selection = ID;
        final String[] selectionArgs = new String[]{
                split[1]
        };
        return getDataColumn(context, contentUri, selection, selectionArgs);
    }

    /**
     * @param context       context of the screen
     * @param uri           uri to identify the file
     * @param selection     selection to filter the column from database
     * @param selectionArgs selection of the column to get data
     * @return String value
     */
    private static String getDataColumn(Context context, Uri uri, String selection, String[]
            selectionArgs) {
        Cursor cursor = null;
        final String column = DATA;
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri Uri that is required to check
     * @return boolean value (true/false)
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return KEY_URI_EXTERNAL_STORAGE_DOCUMENTS.equals(uri.getAuthority());
    }

    /**
     * @param uri Uri that is required to check
     * @return boolean value (true/false)
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return KEY_URI_DOWNLOADS_DOCUMENTS.equals(uri.getAuthority());
    }

    /**
     * @param uri Uri that is required to check
     * @return boolean value (true/false)
     */
    private static boolean isMediaDocument(Uri uri) {
        return KEY_URI_MEDIA_DOCUMENTS.equals(uri.getAuthority());
    }

}
