var exec = require('cordova/exec');

var GooglePayButton = {
  __buttonIsVisible: false,

  __paymentHandlerCallback: null,

  __attachedToElement: null,
  __attachedToElementPosition: null,

  __paymentEventListener: function (event) {
    if (typeof GooglePayButton.__paymentHandlerCallback === "function")
      GooglePayButton.__paymentHandlerCallback(event.detail);
  },
  __dispose: function () {
    this.__attachedToElement = null;
    this.__attachedToElementPosition = null;

    this.__buttonIsVisible = false;

    document.removeEventListener("gpay", this.__paymentEventListener);
    this.__paymentMonitorCallback = null;
  },
  __monitorAttachedElementPosition: function () {
    if (this.__attachedToElement && this.__buttonIsVisible) {
      const rect = this.__attachedToElement.getBoundingClientRect();
      if (!this.__attachedToElementPosition || this.__attachedToElementPosition.left != rect.left || this.__attachedToElementPosition.top != rect.top) {
        this.__attachedToElementPosition = {
          left: rect.left,
          top: rect.top
        };

        this.setPosition(this.__attachedToElementPosition);
      }

      requestAnimationFrame(this.__monitorAttachedElementPosition.bind(this));
    }
  },

  // API methods
  create: function (environment, buttonOptions, paymentDataRequest, successCallback, errorCallback) {
    this.__dispose();
    document.addEventListener("gpay", this.__paymentEventListener);
    exec(successCallback, errorCallback, 'GooglePayButton', 'create', [environment, buttonOptions, paymentDataRequest]);
  },
  setPaymentHandler: function (paymentHandlerCallback) {
    this.__paymentMonitorCallback = paymentHandlerCallback;
  },
  attachToElement: function (domElement) {
    this.__attachedToElementPosition = null;
    this.__attachedToElement = domElement;

    if (this.__buttonIsVisible && this.__attachedToElement)
      this.__monitorAttachedElementPosition();
  },
  setPosition: function (buttonPosition, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'GooglePayButton', 'setPosition', [buttonPosition]);
  },
  setVisibility: function (visible, successCallback, errorCallback) {
    const restoreMonitoring = this.__buttonIsVisible != visible && visible && this.__attachedToElement;

    this.__buttonIsVisible = visible;
    if (restoreMonitoring)
      this.__monitorAttachedElementPosition();

    exec(successCallback, errorCallback, 'GooglePayButton', 'setVisibility', [visible]);
  },
  destroy: function (successCallback, errorCallback) {
    this.__dispose();
    exec(successCallback, errorCallback, 'GooglePayButton', 'destroy', []);
  }
};

module.exports = GooglePayButton;
