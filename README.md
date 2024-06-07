# cordova-plugin-gpay-button

A plugin to display the native GPay button as googd as possible into awebview.

## Installation:

For stable relases type:

```shell
cordova plugin add @bkamenov/cordova-plugin-gpay-button
```



For latest releases type:

```shell
cordova plugin add https://github.com/bkamenov/cordova-plugin-gpay-button
```

## API & Usage:

**IMPORTANT:** You may have ONLY one instance of the GPay button at a time.

```js
const environment = "TEST"; // GooglePay environment: "TEST" or "PRODUCTION"
const buttonOptions = {
  buttonTheme: "DARK", // "DARK" or "LIGHT"
  buttonType: "BUY", // options: "BOOK", "BUY", "CHECKOUT", "DONATE", "ORDER", "PAY", "PLAIN", "SUBSCRIBE"
  buttonCornerRadius: 4, // any positive integer up to 100
};

//You can adjust it as you wish (see google.payments.api.PaymentDataRequest)
const paymentDataRequest = {
   apiVersion: 2,
  apiVersionMinor: 0,
  allowedPaymentMethods: [
    {
      type: 'CARD',
      parameters: {
        allowedAuthMethods: ['PAN_ONLY', 'CRYPTOGRAM_3DS'],
        allowedCardNetworks: ['AMEX', 'MASTERCARD', 'VISA']
      },
      tokenizationSpecification: {
        type: 'PAYMENT_GATEWAY',
        parameters: {
          gateway: 'example', //This is your payment service provider e.g. "stripe"
          gatewayMerchantId: 'exampleGatewayMerchantId' //Your merchant ID within "stripe
        }
      }
    }
  ],
  merchantInfo: {
    //merchantId: '01234567890123456789', //Your Google merchant ID. Uncomment in PRODUCTION with real ID.
    merchantName: 'Example Merchant' //Your Google merchant name
  },
  transactionInfo: {
    totalPriceStatus: 'FINAL',
    totalPrice: '12.34',
    currencyCode: 'USD',
    countryCode: 'US'
  }
};

const buttonAnchor = document.getElementById("gpay-anchor");
if (!buttonAnchor) {
  console.error("Missing GPay button anchor!");
  return;
}

const rect = buttonAnchor.getBoundingClientRect();
const buttonPosition = {
  top: rect.top,    // integer in pixels. Negative values are allowed. 
  left: rect.left   // integer in pixels. Negative values are allowed. 
};

cordova.plugins.GooglePayButton.create(envirionment, buttonOptions, paymentDataRequest, 
(result) => {
  if(result.canMakePayments) { //You may show the button and use it
    // Set button position - this method is useful if your button 
    // position never changes or changes from time to time.
    // You can call the position method as many times as you wish.
    cordova.plugins.GooglePayButton.setPosition(buttonPosition);

    // If your GPay button needs to be scrolled or just adjusted to a 
    // changing position of the anchor element, you can make this smoothly
    // by calling:
    cordova.plugins.GooglePayButton.attachToElement(buttonAnchor);
    // NOTE: To unanchor the GPay button pass 'null' as an argument to 'attachToElement'.
    // NOTE: You can set another anchor element for the GPay button anytime.

    // Show the button. Passing 'false' will hide the GPay button.
    // NOTE: You can show/hide the button anytime.
    cordova.plugins.GooglePayButton.setVisibility(true);

    // Set the payment handler.
    // paymentResult.paymentData is of type google.payments.api.PaymentData
    cordova.plugins.GooglePayButton.setPaymentHandler((paymentResult) => {
      if (paymentResult.status === "OK") {
        const paymentToken = paymentResult.paymentData.paymentMethodData.tokenizationData.token;
        //TODO: Send the token to your server and process it with your gateway (e.g. stripe)
      }
      else if (paymentResult.status === "ERROR") {
        console.error("GPay error: " + paymentResult.error!);
      }
      else if (paymentResult.status === "CANCELED") {
        console.log("User canceled the payment. Nothing bad happened.");
      }
    });
  }
},
(error) => {
  console.error("GPay button error: " + error);
});

...

// At some point you should have finished your work with the button
// and it should be destroyed by calling:
cordova.plugins.GooglePayButton.destroy();
// NOTE: If you just need to change the price or so, but you are
// still on the same page you can just call:
// 'cordova.plugins.GooglePayButton.create' again - it will
// destroy the previous instance for you, so you won't need to 
// explicitly call the 'destroy()' method.
```



You may define an anchor element for your button (the button is a native Android view drawn over the Cordova's webview and position handling is needed).

The button anchor should be with the size of the GPay button as it is defined by buttonType in the create method. You can set the background of your anchor element to red, open the Chrome debugger and play with the CSS to find the right size for your button type. Finally, you can set the background of the anchor element to transparent.



```html
...
<div id="gpay-anchor" style="width: 302px; height: 51px; background-color: red"></div>
...
```

If you like my work and want more nice plugins, you can get me a [beer or stake](https://www.paypal.com/donate/?business=RXTV6JES35UQW&amount=5&no_recurring=0&item_name=Let+me+create+more+inspiring+Cordova+plugins.&currency_code=EUR).


