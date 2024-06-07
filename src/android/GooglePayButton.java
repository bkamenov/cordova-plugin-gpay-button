package com.cordova.plugin.gpaybutton;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.view.Gravity;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.WalletConstants;
import com.google.android.gms.wallet.button.PayButton;
import com.google.android.gms.wallet.button.ButtonOptions;
import com.google.android.gms.wallet.button.ButtonConstants;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;

public class GooglePayButton extends CordovaPlugin {
  private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;

  private FrameLayout buttonContainer = null;
  private PayButton payButton = null;
  private PaymentsClient paymentsClient = null;

  private boolean errorInUiThread = false;

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

    if (action.equals("create")) {
      return this.create(args, callbackContext);
    } else if (action.equals("setPosition")) {
      return this.setPosition(args, callbackContext);
    } else if (action.equals("setVisibility")) {
      return this.setVisibility(args, callbackContext);
    } else if (action.equals("destroy")) {
      return this.destroy(callbackContext);
    } else {
      callbackContext.error("Invalid action");
      return false;
    }
  }

  private boolean create(JSONArray args, CallbackContext callbackContext) {
    try {
      String environment = args.getString(0);
      JSONObject buttonOptions = args.getJSONObject(1);
      JSONObject paymentDataRequest = args.getJSONObject(2);

      JSONObject paymentMethodParams = new JSONObject();
      paymentMethodParams.put("allowedCardNetworks", paymentDataRequest.getJSONArray("allowedPaymentMethods").getJSONObject(0).getJSONObject("parameters").getJSONArray("allowedCardNetworks"));
      paymentMethodParams.put("allowedAuthMethods", paymentDataRequest.getJSONArray("allowedPaymentMethods").getJSONObject(0).getJSONObject("parameters").getJSONArray("allowedAuthMethods"));
      
      JSONObject paymentMethod = new JSONObject();
      paymentMethod.put("type", paymentDataRequest.getJSONArray("allowedPaymentMethods").getJSONObject(0).getString("type"));
      paymentMethod.put("parameters", paymentMethodParams);
      
      JSONArray allowedPaymentMethods = new JSONArray();
      allowedPaymentMethods.put(paymentMethod);

      JSONObject isReadyToPayRequest = new JSONObject();
      isReadyToPayRequest.put("apiVersion", paymentDataRequest.getInt("apiVersion"));
      isReadyToPayRequest.put("apiVersionMinor", paymentDataRequest.getInt("apiVersionMinor"));
      isReadyToPayRequest.put("allowedPaymentMethods", allowedPaymentMethods);

      int environmentConstant = "PRODUCTION".equals(environment) ? WalletConstants.ENVIRONMENT_PRODUCTION : WalletConstants.ENVIRONMENT_TEST;
      Wallet.WalletOptions walletOptions = new Wallet.WalletOptions.Builder().setEnvironment(environmentConstant).build();
      this.paymentsClient = Wallet.getPaymentsClient(cordova.getActivity(), walletOptions);

      //Create a waiting mechanism for the UI code to complete 
      final CountDownLatch latch = new CountDownLatch(1);
      this.errorInUiThread = false;

      //Run on the UI thread
      cordova.getActivity().runOnUiThread(() -> {
        try {
          JSONArray paymentMethods = isReadyToPayRequest.getJSONArray("allowedPaymentMethods");
          int buttonCornerRadius = buttonOptions.getInt("buttonCornerRadius");
          int buttonTheme = buttonOptions.getString("buttonTheme") == "LIGHT" ? ButtonConstants.ButtonTheme.LIGHT : ButtonConstants.ButtonTheme.DARK;
          int buttonType = ButtonConstants.ButtonType.BUY;
          switch (buttonOptions.getString("buttonType")) {
            case "BUY":
              buttonType = ButtonConstants.ButtonType.BUY;
              break;

            case "BOOK":
              buttonType = ButtonConstants.ButtonType.BOOK;
              break;

            case "CHECKOUT":
              buttonType = ButtonConstants.ButtonType.CHECKOUT;
              break;

            case "DONATE":
              buttonType = ButtonConstants.ButtonType.DONATE;
              break;

            case "ORDER":
              buttonType = ButtonConstants.ButtonType.ORDER;
              break;

            case "PAY":
              buttonType = ButtonConstants.ButtonType.PAY;
              break;

            case "PLAIN":
              buttonType = ButtonConstants.ButtonType.PLAIN;
              break;

            case "SUBSCRIBE":
              buttonType = ButtonConstants.ButtonType.SUBSCRIBE;
              break;
          }
          
          // Get parent layout
          FrameLayout parentLayout = (FrameLayout) cordova.getActivity().findViewById(android.R.id.content);

          // Remove any existing button
          this.destroy();

          // Create the PayButton programmatically
          this.payButton = new PayButton(cordova.getActivity());

          this.payButton.initialize(
              ButtonOptions.newBuilder()
                  .setButtonTheme(buttonTheme)
                  .setButtonType(buttonType)
                  .setCornerRadius(buttonCornerRadius)
                  .setAllowedPaymentMethods(paymentMethods.toString())
                  .build());

          FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
          containerParams.gravity = Gravity.TOP | Gravity.LEFT;
          containerParams.setMargins(0, 0, 0, 0);

          FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
          buttonParams.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
          
          this.buttonContainer = new FrameLayout(cordova.getActivity());
          this.buttonContainer.addView(this.payButton, buttonParams);
          parentLayout.addView(this.buttonContainer, containerParams);

          this.payButton.setOnClickListener(view -> {
            // Trigger payment process
            this.requestPayment(paymentDataRequest);
          });

          this.payButton.setVisibility(View.GONE);
        } 
        catch (Exception e) {
          callbackContext.error(e.getMessage());
          this.errorInUiThread = true;
        }
        finally {
          // Signal that the work is done
          latch.countDown();
        }
      });

      //Wait for the UI code to complete
      latch.await();

      //We got an issue during UI operations?
      if(this.errorInUiThread)
        return false;

      IsReadyToPayRequest readyToPayRequest = IsReadyToPayRequest.fromJson(isReadyToPayRequest.toString());
      this.paymentsClient.isReadyToPay(readyToPayRequest).addOnCompleteListener(task -> {
        boolean paymentAvailable = task.isSuccessful() && task.getResult();
        JSONObject result = new JSONObject();

        try {
          result.put("canMakePayments", paymentAvailable);
          callbackContext.success(result);
        }
        catch(Exception e) {
          callbackContext.error(e.getMessage());
        }
      });
      
      return true;
    }
    catch(Exception e) {
      callbackContext.error(e.getMessage());
      return false;
    }
  }

  private boolean setPosition(JSONArray args, CallbackContext callbackContext) {
    try {
      if(this.payButton == null)
        throw new Exception("GPay button is not created.");

      JSONObject buttonPosition = args.getJSONObject(0);

      final int left = buttonPosition.getInt("left");
      final int top = buttonPosition.getInt("top");

      //Create a waiting mechanism for the UI code to complete 
      final CountDownLatch latch = new CountDownLatch(1);
    
      cordova.getActivity().runOnUiThread(() -> {
        FrameLayout.LayoutParams containerParams = (FrameLayout.LayoutParams)this.buttonContainer.getLayoutParams();
        containerParams.setMargins(this.convertCssToAndroidPixels(left), this.convertCssToAndroidPixels(top), 0, 0);
        this.buttonContainer.setLayoutParams(containerParams);

        // Signal that the work is done
        latch.countDown();
      });

      //Wait for the UI code to complete
      latch.await();

      callbackContext.success();
      
      return true;
    }
    catch(Exception e) {
      callbackContext.error(e.getMessage());
      return false;
    }
  }

  private boolean setVisibility(JSONArray args, CallbackContext callbackContext) {
    try {
      if(this.payButton == null)
        throw new Exception("GPay button is not created.");

      final int visibility = args.getBoolean(0) == true ? View.VISIBLE : View.GONE;

      //Create a waiting mechanism for the UI code to complete 
      final CountDownLatch latch = new CountDownLatch(1);

      cordova.getActivity().runOnUiThread(() -> {
        this.payButton.setVisibility(visibility);
    
        // Signal that the work is done
        latch.countDown();
      });

      //Wait for the UI code to complete
      latch.await();

      callbackContext.success();
      return true;
    }
    catch(Exception e) {
      callbackContext.error(e.getMessage());
      return false;
    }
  }

  private boolean destroy(CallbackContext callbackContext) {
    try {
      //Create a waiting mechanism for the UI code to complete 
      final CountDownLatch latch = new CountDownLatch(1);
    
      cordova.getActivity().runOnUiThread(() -> {
        this.destroy();

        // Signal that the work is done
        latch.countDown();
      });

      //Wait for the UI code to complete
      latch.await();

      callbackContext.success();
      return true;
    }
    catch(Exception e) {
      callbackContext.error(e.getMessage());
      return false;
    }
  }

  // Must be executed on the UI thread 
  private void destroy() {
    if (this.buttonContainer != null) {
      FrameLayout parentLayout = (FrameLayout) cordova.getActivity().findViewById(android.R.id.content);
      parentLayout.removeView(this.buttonContainer);
      this.buttonContainer = null;
      this.payButton = null;
    }
  }

  private int convertCssToAndroidPixels(int cssPixels) {
    float scale = getWebViewScale();
    float androidPixels = (float)cssPixels * scale;
    
    return (int)androidPixels;
  }

  private float getWebViewScale() {
    WebView webView = (WebView) this.webView.getEngine().getView();
    return webView.getScale();
  }

  private void requestPayment(JSONObject paymentDataRequest) {
    try {
      PaymentDataRequest request = PaymentDataRequest.fromJson(paymentDataRequest.toString());
      cordova.setActivityResultCallback(this);
      AutoResolveHelper.resolveTask(
        paymentsClient.loadPaymentData(request),
        this.cordova.getActivity(),
        LOAD_PAYMENT_DATA_REQUEST_CODE);
    }
    catch(Exception e) {
      Log.e("GooglePayButton", "Error starting GPay payment process: " + e.getMessage());
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == LOAD_PAYMENT_DATA_REQUEST_CODE) {
      switch (resultCode) {
        case Activity.RESULT_OK:
            PaymentData paymentData = PaymentData.getFromIntent(data);
            handlePaymentSuccess(paymentData);
            break;
        case Activity.RESULT_CANCELED:
            this.handlePaymentCanceled();
            break;
        case AutoResolveHelper.RESULT_ERROR:
            Status status = AutoResolveHelper.getStatusFromIntent(data);
            this.handlePaymentError(this.getMessageFromStatus(status));
            break;
        default:
            this.handlePaymentError("ERROR_UNKNOWN: Unknown result code: " + resultCode);
      }
    }
  }

  private void handlePaymentSuccess(PaymentData paymentData) {
    try {
      String paymentInformation = paymentData.toJson();
      JSONObject paymentResponse = new JSONObject(paymentInformation);

      JSONObject result = new JSONObject();
      result.put("status", "OK");
      result.put("paymentData", paymentResponse);
      this.fireGpayEvent(result);
    }
    catch(Exception e) {
      Log.e("GooglePayButton", "Error handling canceled payment: " + e.getMessage());
    }
  }

  private void handlePaymentCanceled() {
    try {
      JSONObject result = new JSONObject();
      result.put("status", "CANCELED");
      this.fireGpayEvent(result);
    }
    catch(Exception e) {
      Log.e("GooglePayButton", "Error handling canceled payment: " + e);
    }
  }

  private void handlePaymentError(String errorMsg) {
    try {
      JSONObject result = new JSONObject();
      result.put("status", "ERROR");
      result.put("error", errorMsg);
      this.fireGpayEvent(result);
    }
    catch(Exception e) {
      Log.e("GooglePayButton", "Error handling payment error: " + e.getMessage());
    }
  }

  private String getMessageFromStatus(Status status) {
    int statusCode = status.getStatusCode();
    String statusMessage = status.getStatusMessage();

    switch (statusCode) {
      case WalletConstants.ERROR_CODE_BUYER_ACCOUNT_ERROR:
          // Handle buyer account error
          return "ERROR_CODE_BUYER_ACCOUNT_ERROR: " + statusMessage;
      case WalletConstants.ERROR_CODE_INVALID_PARAMETERS:
          // Handle invalid parameters
          return "ERROR_CODE_INVALID_PARAMETERS: " + statusMessage;
      case WalletConstants.ERROR_CODE_AUTHENTICATION_FAILURE:
          // Handle authentication failure
          return "ERROR_CODE_AUTHENTICATION_FAILURE: " + statusMessage;
      case WalletConstants.ERROR_CODE_MERCHANT_ACCOUNT_ERROR:
          // Handle merchant account error
          return "ERROR_CODE_MERCHANT_ACCOUNT_ERROR: " + statusMessage;
      case WalletConstants.ERROR_CODE_SERVICE_UNAVAILABLE:
          // Handle service unavailable
          return "ERROR_CODE_SERVICE_UNAVAILABLE: " + statusMessage;
      case WalletConstants.ERROR_CODE_UNSUPPORTED_API_VERSION:
          // Handle unsupported API version
          return "ERROR_CODE_UNSUPPORTED_API_VERSION: " + statusMessage;
      case WalletConstants.ERROR_CODE_UNKNOWN:
          // Handle unknown error
          return "ERROR_CODE_UNKNOWN: " + statusMessage;
      case WalletConstants.ERROR_CODE_INTERNAL_ERROR:
          // Handle internal error
          return "ERROR_CODE_INTERNAL_ERROR: " + statusMessage;
      default:
          // Handle other errors
          return "ERROR_UNKNOWN: " + statusMessage;
    }
  }

  private void fireGpayEvent(JSONObject eventData) {
    final String jsEventData = eventData.toString();

    cordova.getActivity().runOnUiThread(() -> {
      WebView webView = (WebView) this.webView.getEngine().getView();
      String js = String.format("document.dispatchEvent(new CustomEvent('gpay', { detail: %s }));", jsEventData);
      webView.evaluateJavascript(js, null);
    });
  }
}
