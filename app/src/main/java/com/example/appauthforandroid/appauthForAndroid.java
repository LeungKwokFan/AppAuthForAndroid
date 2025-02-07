package com.example.appauthforandroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.auth0.android.jwt.Claim;
import com.auth0.android.jwt.JWT;
import com.bumptech.glide.Glide;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

import java.util.Map;

public class appauthForAndroid extends AppCompatActivity {

    private static String AUTHORIZATION_ENDPOINT_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static String TOKEN_ENDPOINT_URI = "https://www.googleapis.com/oauth2/v4/token";
    private static final String REVOCATION_ENDPOINT_URI = "https://accounts.google.com/o/oauth2/revoke";
    private static final String REDIRECT_URI_SCHEME = "com.example.appauthforandroid";
    private static final String REDIRECT_URI_PATH = "/oauth2redirect";
    private static final String CLIENT_ID = "{your client id}";
    private static final String SCOPE = "openid email profile";
    public static final int REQUEST_CODE = 100;

    private AuthorizationService authService;
    private AuthState authState;

    private TextView GoogleGivenNameTextView;
    private TextView GoogleFamilyNameTextView;
    private ImageView ProfileImageView;
    private TextView GoogleMessageTextview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_page);

        Button GoogleRequestButton = findViewById(R.id.GoogleRequestButton);
        Button GoogleSignOutButton = findViewById(R.id.GoogleSignOutButton);

        ProfileImageView = findViewById(R.id.ProfileImageView);
        GoogleGivenNameTextView = findViewById(R.id.GoogleGivenNameTextView);
        GoogleFamilyNameTextView = findViewById(R.id.GoogleFamilyNameTextView);
        GoogleMessageTextview = findViewById(R.id.GoogleMessageTextview);

        authService = new AuthorizationService(this);
        authState = new AuthState();

        GoogleRequestButton.setOnClickListener(v -> performAuthorization());
        GoogleSignOutButton.setOnClickListener(v -> revokeAccess());
    }

    private void performAuthorization() {
        AuthorizationRequest request = new AuthorizationRequest.Builder(
                new AuthorizationServiceConfiguration(
                        Uri.parse(AUTHORIZATION_ENDPOINT_URI),
                        Uri.parse(TOKEN_ENDPOINT_URI)
                ),
                CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(REDIRECT_URI_SCHEME + ":" + REDIRECT_URI_PATH))
                .setScope(SCOPE).build();

        Log.d("AuthDebug", "Authorization Request: " + request);

        Intent authIntent = authService.getAuthorizationRequestIntent(request);

        startActivityForResult(authIntent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("AuthDebug", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d("AuthDebug", "Result OK, handling authorization response.");
                handleAuthorizationResponse(data);
            } else {
                Log.e("AuthDebug", "Result not OK, exiting.");
            }
        }
    }

    private void handleAuthorizationResponse(Intent data) {
        AuthorizationResponse response = AuthorizationResponse.fromIntent(data);
        AuthorizationException ex = AuthorizationException.fromIntent(data);

        if (response != null) {
//            Log.d("AuthDebug", "Authorization Response: " + response);
            authState.update(response, ex);
            performTokenRequest(response);
        } else {
            Log.e("AuthError", "Authorization response is null");
            if (ex != null) {
                Log.e("AuthError", "Authorization error: " + ex);
            }
        }
    }

    private void performTokenRequest(AuthorizationResponse response) {
        TokenRequest tokenRequest = response.createTokenExchangeRequest();
        Log.d("AuthDebug", "Token Request: " + tokenRequest);

        authService.performTokenRequest(tokenRequest, new AuthorizationService.TokenResponseCallback() {
            @Override
            public void onTokenRequestCompleted(
                    TokenResponse tokenResponse,
                    AuthorizationException authException) {

                if (tokenResponse != null) {
                    Log.d("AuthDebug", "Token Response: " + tokenResponse);
                    authState.update(tokenResponse, authException);
                    fetchUserInfo(authState.getIdToken());
                } else {
                    if (authException != null) {
                        Log.e("AuthError", "Token response is null: " + authException);
                    }
                }
            }
        });
    }

    private void fetchUserInfo(String idToken) {
        JWT jwt = new JWT(idToken);
        Map<String, Claim> claims = jwt.getClaims();

        Log.d("AuthDebug", "ID Token Claims: " + claims.toString());

        String name = claims.containsKey("name") ? claims.get("name").asString() : null;
        String familyName = claims.containsKey("family_name") ? claims.get("family_name").asString() : null;
        String givenName = claims.containsKey("given_name") ? claims.get("given_name").asString() : null;
        String pictureUrl = claims.containsKey("picture") ? claims.get("picture").asString() : null;
        String email = claims.containsKey("email") ? claims.get("email").asString() : null;

        GoogleGivenNameTextView.setText(givenName);
        GoogleFamilyNameTextView.setText(familyName);

        if (pictureUrl != null) {
            Glide.with(this)
                    .load(pictureUrl)
                    .into(ProfileImageView);
        }

        GoogleMessageTextview.append("\nSign up Account: " + email + "\nUser name: " + name);
    }

    private void revokeAccess() {
        String accessToken = authState.getAccessToken();
        if (accessToken != null) {
            String url = "https://accounts.google.com/o/oauth2/revoke?token=" + accessToken;
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            GoogleMessageTextview.append("\nUser Log Out Successfully!");
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Toast.makeText(appauthForAndroid.this, "Logout failed", Toast.LENGTH_SHORT).show();
                        }
                    });

            requestQueue.add(stringRequest);
        } else {
            Toast.makeText(this, "No access token found", Toast.LENGTH_SHORT).show();
            GoogleMessageTextview.append("\nLogout Button Clicked when no access token found!");
        }
    }
}