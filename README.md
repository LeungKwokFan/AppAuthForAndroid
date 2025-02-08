# AppAuthForAndroid


## Background

For some reasons, some special brands' phones can not use GMS (google mobile services) since 2020. However, for some apps, it's necessary to use google account to authorize. In this situation we can use AppAuth to achieve this goal. This project is a simple demo to get the information of the google account then send back to our application.

## Project Preparation

Before using AppAuth Android SDK, some libiaries should be integated into the project.

### 1. Compilation dependencies

In the app-level `build.gradle`, add:
```
implementation 'net.openid:appauth:0.11.1'// AppAuth SDK
implementation 'com.auth0.android:jwtdecode:2.0.0'// For decode ID Token
implementation 'com.android.volley:volley:1.2.1'// Volley
```
and
```
defaultConfig {
    ...
    manifestPlaceholders = [
            appAuthRedirectScheme: '{your package name}'
    ]
}
```

#### Possible Issue and Solution
If entercounters:
```
Error:
Failed to resolve: com.android.volley:volley:1.1.1
```
It's caused by deprecation of `JCenter()`. The volley libiary of version 1.2.0 and newer now is supported by `mavenCenter()`.

Solution 1: In project-level `build.grandle`, add:
```
allprojects {
    repositories {
         jcenter() \\add this line
    }
}
```
Solution 2: use `'com.android.volley:volley:1.2.0'` or newer version instead.

### 2. Add RedirectUriReceiverActivity

In `AndroidManifest.xml`, add:
```
<activity
    android:name="net.openid.appauth.RedirectUriReceiverActivity"
    android:exported="true"
    tools:node="replace">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme={your package name}/>
    </intent-filter>
</activity>
```
and

`<uses-permission android:name="android.permission.INTERNET" />`.

### 3. Google Cloud Console Preparation

#### Steps before Google Console

Since Google Cloud Platform needs the fingerprint of our project to verify. We'd better prepare it at first.

For example, for Huawei phones, we can follow the official tutorial: 
CN Version: https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/config-agc-0000001050196065#section147011294331
EN Version: https://developer.huawei.com/consumer/en/doc/HMSCore-Guides/config-agc-0000001050196065#section147011294331

##### Potential Problem

When using JDK to obtain the SHA-256 certificate fingerprint, if your default language of JDK (on Windows, I have not tried on Mac) is not English, there is (maybe) a translation problem, which means the "SHA-1" and "SHA-256" will be translated wrongly as "MD5" and "SHA-1". For avoiding this question, you can set your default language of JDK as English.
For reference: https://stackoverflow.com/questions/67447237/keytool-sha256withrsa-written-instead-of-an-actual-sha256-on-my-production-key


#### Steps in Console

1. Create a project on Google Cloud Platform, and select `APIs&Services` -> `OAuth consent screen`. For example, the scope we wanna collect is `"openid email profile"`, then we select userinfo.email, userinfo.profile, and openid in the non-sensitive scopes form. Finally publish the app.
2. Next, choose `credentials` tab and select `OAuth client ID` option. Choose `Android` and fill the corresponding information like package name. And fill the `SHA-1` key acquired by using keytool command on `.jks` file which created in Android Studio.
3. After clicking create button, the OAtuh client ID can be created which will be used in the coding part.


## Coding

### Log in Coding

#### 1. Define and Create Instances

Firstly for convenience we can define the URL and others we will use later:

```
private static String AUTHORIZATION_ENDPOINT_URI = "https://accounts.google.com/o/oauth2/v2/auth";
private static String TOKEN_ENDPOINT_URI = "https://www.googleapis.com/oauth2/v4/token";
private static final String REVOCATION_ENDPOINT_URI = "https://accounts.google.com/o/oauth2/revoke";
private static final String REDIRECT_URI_SCHEME = "{your package name}";
private static final String REDIRECT_URI_PATH = "/{path}";
private static final String CLIENT_ID = "{your client ID}";
private static final String SCOPE = "openid email profile"; //depends on your requirement
public static final int REQUEST_CODE = 100;
```

Besides, we can also create the instances of some classes:

```
authService = new AuthorizationService(this);  //to mainly handle the process
authState = new AuthState();  //to record the state
```

Of course, we can define the config of authenticaiton:
```
AuthorizationServiceConfiguration authConfig = new AuthorizationServiceConfiguration(
                        Uri.parse(AUTHORIZATION_ENDPOINT_URI),
                        Uri.parse(TOKEN_ENDPOINT_URI))
```


#### 2. Request and Handle results

Create a request by using the config we created :
```
AuthorizationRequest request = new AuthorizationRequest.Builder(
                authConfig,
                CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(REDIRECT_URI_SCHEME + ":" + REDIRECT_URI_PATH))
                .setScope(SCOPE).build();
```

Then use an intent to receive the result, and rewrite the in-built function:
```
Intent authIntent = authService.getAuthorizationRequestIntent(request);
startActivityForResult(authIntent, REQUEST_CODE);
```
and
```
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
        AuthorizationResponse response = AuthorizationResponse.fromIntent(intent);
        AuthorizationException ex = AuthorizationException.fromIntent(intent);
        if (response != null) {
            authState.update(response, ex);
            //Here show how you process the response
        } else {
            //Here for error
        }
    }
}
```

#### 3. EXAMPLE: Get basic Info of Google account

Function `performTokenRequest()` to define the logic of processing `response`:
```
private void performTokenRequest(AuthorizationResponse response) {
    TokenRequest tokenRequest = response.createTokenExchangeRequest();
    authService.performTokenRequest(tokenRequest, new AuthorizationService.TokenResponseCallback() {
        @Override
        public void onTokenRequestCompleted(
                TokenResponse tokenResponse,
                AuthorizationException authException) {
            authState.update(tokenResponse, authException);
            if (tokenResponse != null) {
                String accessToken = authState.getAccessToken();
                fetchUserInfo(idToken);
            } else {
                if (authException != null) {
                    Log.e("authException", "AuthException is not null!");
                }
            }
        }
    });
}
```

After that, we use `JWT` to fetch the basic information of Google account in our scopes we defined:
```
private void fetchUserInfo(String idToken) {
    JWT jwt = new JWT(idToken);
    Map<String, Claim> claims = jwt.getClaims();

    String name = claims.containsKey("name") ? claims.get("name").asString() : null;
    String familyName = claims.containsKey("family_name") ? claims.get("family_name").asString() : null;
    String givenName = claims.containsKey("given_name") ? claims.get("given_name").asString() : null;
    String pictureUrl = claims.containsKey("picture") ? claims.get("picture").asString() : null;
    String email = claims.containsKey("email") ? claims.get("email").asString() : null;

    //update on the component
}
```

### Log out

In this project, we use `Volley` we imported before to process the logout request:
```
String url = "https://accounts.google.com/o/oauth2/revoke?token=" + accessToken;
RequestQueue requestQueue = Volley.newRequestQueue(this);
StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
        new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                //handle the successful response
            }
        },
        new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // handle the error
            }
        });
requestQueue.add(stringRequest);
```
in which `accessToken` is a part of information in updated `authState`.


### Other problems

#### Application shows "Access Blocked, the request is invalid"

Solution: 
1. In the Google Console, open the 'Credentials' section on the left side.
2. Under the 'OAuth 2.0 Client IDs' section select the Android device.
3. Scroll down and select 'Advanced Settings'.
4. Enable 'Enable custom URI scheme' anyway then click Save.
 
 
