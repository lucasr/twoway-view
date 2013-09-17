/*
 * Copyright (C) 2012 Lucas Rocha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lucasr.twowayview.sample;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;

public class HttpHelper {
    static private String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        try {
            String line = null;

            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return sb.toString();
    }

    static public JSONArray loadJSON(String url) {
        HttpURLConnection connection = null;
        JSONArray json = null;
        InputStream is = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);

            is = new BufferedInputStream(connection.getInputStream());
            json = new JSONArray(convertStreamToString(is));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (JSONException je) {
            je.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            if (connection != null) {
                connection.disconnect();
            }
        }

        return json;
    }

    static public InputStream loadImage(String url) {
        HttpURLConnection connection = null;
        InputStream is = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);

            is = new BufferedInputStream(connection.getInputStream());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return is;
    }
}
