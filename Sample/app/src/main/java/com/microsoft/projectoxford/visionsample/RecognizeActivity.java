//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license.
//
// Microsoft Cognitive Services (formerly Project Oxford): https://www.microsoft.com/cognitive-services
//
// Microsoft Cognitive Services (formerly Project Oxford) GitHub:
// https://github.com/Microsoft/Cognitive-Vision-Android
//
// Copyright (c) Microsoft Corporation
// All rights reserved.
//
// MIT License:
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED ""AS IS"", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
package com.microsoft.projectoxford.visionsample;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.contract.LanguageCodes;
import com.microsoft.projectoxford.vision.contract.Line;
import com.microsoft.projectoxford.vision.contract.OCR;
import com.microsoft.projectoxford.vision.contract.Region;
import com.microsoft.projectoxford.vision.contract.Word;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;
import com.microsoft.projectoxford.visionsample.helper.ImageHelper;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.*;


public class RecognizeActivity extends ActionBarActivity {

    // Flag to indicate which task is to be performed.
    private static final int REQUEST_SELECT_IMAGE = 0;

    // The button to select an image
    private Button mButtonSelectImage;

    // The URI of the image selected to detect.
    private Uri mImageUri;

    // The image selected to detect.
    private Bitmap mBitmap;

    // The edit to show status and result.
    private EditText mEditText;

    private VisionServiceClient client;
    private static int LONG_CLICK_TIMES = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);

        if (client==null){
            client = new VisionServiceRestClient(getString(R.string.subscription_key));
        }

        mButtonSelectImage = (Button)findViewById(R.id.buttonSelectImage);
        mEditText = (EditText)findViewById(R.id.editTextResult);



        mEditText.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                System.out.println("No of times called: "+LONG_CLICK_TIMES);
                int startSelection=mEditText.getSelectionStart();
                int endSelection=mEditText.getSelectionEnd();

                String selectedText = mEditText.getText().toString().substring(startSelection, endSelection);
                System.out.println("Selected Text: "+ selectedText);
                LONG_CLICK_TIMES++;
                RestCallAndDisplay task = new RestCallAndDisplay();
                task.execute(new String[] {selectedText});
                return true;
            }
        });




    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_recognize, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Called when the "Select Image" button is clicked.
    public void selectImage(View view) {
        mEditText.setText("");

        Intent intent;
        intent = new Intent(RecognizeActivity.this, com.microsoft.projectoxford.visionsample.helper.SelectImageActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    // Called when image selection is done.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("AnalyzeActivity", "onActivityResult");
        switch (requestCode) {
            case REQUEST_SELECT_IMAGE:
                if(resultCode == RESULT_OK) {
                    // If image is selected successfully, set the image URI and bitmap.
                    mImageUri = data.getData();

                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            mImageUri, getContentResolver());
                    if (mBitmap != null) {
                        // Show the image on screen.
                        ImageView imageView = (ImageView) findViewById(R.id.selectedImage);
                        imageView.setImageBitmap(mBitmap);

                        // Add detection log.
                        Log.d("AnalyzeActivity", "Image: " + mImageUri + " resized to " + mBitmap.getWidth()
                                + "x" + mBitmap.getHeight());

                        doRecognize();
                    }
                }
                break;
            default:
                break;
        }
    }


    public void doRecognize() {
        mButtonSelectImage.setEnabled(false);
        mEditText.setText("Analyzing...");

        try {
            new doRequest().execute();
        } catch (Exception e)
        {
            mEditText.setText("Error encountered. Exception is: " + e.toString());
        }
    }

    private String process() throws VisionServiceException, IOException {
        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        OCR ocr;
        ocr = this.client.recognizeText(inputStream, LanguageCodes.AutoDetect, true);

        String result = gson.toJson(ocr);
        Log.d("result", result);

        return result;
    }





    private class RestCallAndDisplay extends AsyncTask<String, String, List<String>> {
        @Override
        protected List<String> doInBackground(String... urls) {
            List<String> urltypes = new ArrayList<>();

            System.out.println("Inside aysnc call: ");
            System.out.println("Value :" +urls[0]);
            String result="";
            if(!urls[0].isEmpty()) {
                try {

                    ArrayList<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
                    nameValuePairs.add(new BasicNameValuePair("lastupdate", "lastupdate"));


                    JSONObject jsonObj = new JSONObject();
                    jsonObj.put("Ocp-Apim-Subscription-Key", "7ef6e4a5ba28408991f91ac8d35093ba");
                    String responsefilter="Webpages";
                    int count=3;
                    String url = "https://api.cognitive.microsoft.com/bing/v5.0/search?q=" + urls[0]+"&responseFilter="+responsefilter+"&count="+count+"&$format=JSON";
                    System.out.println(".................."+url);
                    //HttpPost httpPost = new HttpPost(url);

                    HttpGet httpGet = new HttpGet(url);
                    httpGet.setHeader("Content-Type", "application/json");
                    httpGet.addHeader("Ocp-Apim-Subscription-Key", "7ef6e4a5ba28408991f91ac8d35093ba");
                    StringEntity entity = new StringEntity(jsonObj.toString(), HTTP.UTF_8);
                    //entity.setContentType("application/json");

                    //httpPost.setEntity(entity);
                    //httpGet.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                    HttpClient client = new DefaultHttpClient();
                    HttpResponse response = client.execute(httpGet);


                    String result1 = EntityUtils.toString(response.getEntity());
                    System.out.println("result1....."+result1);
                    JSONObject json_ob=new JSONObject(result1);
                    System.out.println("...."+json_ob);

                    //ArrayList<JSONObject> value_ob=new ArrayList<JSONObject>();

                        JSONArray jArray = new JSONArray(json_ob.getJSONObject("webPages").get("value").toString());
                        System.out.println(jArray);
                    //System.out.println("..."+json_ob.length()+"/////   "+ json_ob.getJSONObject("webPages").get("value"));

                    int len = jArray.length();


                    for(int i = 0; i < len; i++) {

                        String str = jArray.getJSONObject(i).getString("displayUrl");
                        if(str.contains("www")||str.contains("http")) {
                            urltypes.add(str);
                            System.out.println("''''''" + str);
                        }

                    }



                    // CONVERT RESPONSE STRING TO JSON ARRAY
                    /*JSONArray ja = new JSONArray(result1);
                    System.out.println("..........."+ja.length());*/


                   /* HttpEntity httpEntity = response.getEntity();

                    InputStream is = httpEntity.getContent();
                    result = IOUtils.toString(is);
                    JSONObject myObject = new JSONObject(result);*/
                    ///System.out.println("response :..........." + response);


                    /*int n = ja.length();
                    for (int i = 0; i < n; i++) {
                        // GET INDIVIDUAL JSON OBJECT FROM JSON ARRAY
                        JSONObject jo = ja.getJSONObject(i);
                        String name = jo.getString("snippet");
                    System.out.println(name);
                    }*/
                } catch (Exception e) {
                        // Store error
                }
            }

            return urltypes;
        }

        @Override
        protected void onPostExecute(List<String> result) {
            if (result != null && result.size() > 0) {
                StringBuilder stringBuilder = new StringBuilder();
                //Toast.makeText(getApplicationContext(), result.size() + " related URLs found", Toast.LENGTH_LONG).show();
                for (int i = 0; i < result.size(); i++) {
                    //Toast.makeText(getApplicationContext(), result.get(i), Toast.LENGTH_LONG).show();
                    stringBuilder.append(result.get(i));
                    stringBuilder.append(System.getProperty("line.separator"));
                }

                try {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                            RecognizeActivity.this);

                    // set title
                    alertDialogBuilder.setTitle(result.size() + " related URLs found");

                    // set dialog message
                    alertDialogBuilder
                            .setMessage(stringBuilder.toString())
                            .setCancelable(false)
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            })
                    ;

                    // create alert dialog
                    AlertDialog alertDialog = alertDialogBuilder.create();

                    // show it
                    alertDialog.show();
                } catch (Exception e) {
                    System.out.println("Message : " + e.getMessage());
                }
            }

        }
    }











    private class doRequest extends AsyncTask<String, String, String> {
        // Store error message
        private Exception e = null;

        public doRequest() {
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return process();
            } catch (Exception e) {
                this.e = e;    // Store error
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);
            // Display based on error existence

            if (e != null) {
                mEditText.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                Gson gson = new Gson();
                OCR r = gson.fromJson(data, OCR.class);

                String result = "";
                for (Region reg : r.regions) {
                    for (Line line : reg.lines) {
                        for (Word word : line.words) {
                            result += word.text + " ";
                        }
                        result += "\n";
                    }
                    result += "\n\n";
                }

                mEditText.setText(result);
            }
            mButtonSelectImage.setEnabled(true);
        }
    }
}
