package org.nervos.neuron.fragment;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import org.nervos.neuron.R;
import org.nervos.neuron.activity.AboutUsActivity;
import org.nervos.neuron.activity.CurrencyActivity;
import org.nervos.neuron.activity.SimpleWebActivity;
import org.nervos.neuron.view.SettingButtonView;
import org.nervos.neuron.view.dialog.AuthFingerDialog;
import org.nervos.neuron.service.httpservice.HttpUrls;
import org.nervos.neuron.util.ConstUtil;
import org.nervos.neuron.util.fingerprint.AuthenticateResultCallback;
import org.nervos.neuron.util.fingerprint.FingerPrintController;
import org.nervos.neuron.util.db.SharePrefUtil;
import org.nervos.neuron.view.dialog.ToastSingleButtonDialog;

public class SettingsFragment extends NBaseFragment {

    public static final String TAG = SettingsFragment.class.getName();
    private SettingButtonView currencySBV, aboutUsSBV, contactUsSBV, fingerPrintSBV, forumsSBV;
    private static final int Currency_Code = 10001;
    private AuthFingerDialog authFingerDialog = null;
    private FingerPrintController mFingerPrintController;

    @Override
    protected int getContentLayout() {
        return R.layout.fragment_settings;
    }

    @Override
    public void initView() {
        currencySBV = (SettingButtonView) findViewById(R.id.sbv_local_coin);
        aboutUsSBV = (SettingButtonView) findViewById(R.id.sbv_about_us);
        contactUsSBV = (SettingButtonView) findViewById(R.id.sbv_contact_us);
        fingerPrintSBV = (SettingButtonView) findViewById(R.id.sbv_fingerprint);
        forumsSBV = (SettingButtonView) findViewById(R.id.sbv_forums);
    }

    @Override
    public void initData() {
        mFingerPrintController = new FingerPrintController(getActivity());
        currencySBV.setRightText(SharePrefUtil.getString(ConstUtil.CURRENCY, "CNY"));
        if (mFingerPrintController.isSupportFingerprint()) {
            fingerPrintSBV.setVisibility(View.VISIBLE);
            if (SharePrefUtil.getBoolean(ConstUtil.FINGERPRINT, false)) {
                fingerPrintSBV.setSwitch(true);
            } else {
                SharePrefUtil.putBoolean(ConstUtil.FINGERPRINT, false);
                fingerPrintSBV.setSwitch(false);
            }
        } else {
            fingerPrintSBV.setVisibility(View.GONE);
        }
    }

    @Override
    public void initAction() {
        currencySBV.setOpenListener(() -> {
            Intent intent = new Intent(getActivity(), CurrencyActivity.class);
            startActivityForResult(intent, Currency_Code);
        });
        fingerPrintSBV.setSwitchListener((is) -> {
            if (is) {
                //setting fingerprint
                if (mFingerPrintController.hasEnrolledFingerprints() && mFingerPrintController.getEnrolledFingerprints().size() > 0) {
                    if (authFingerDialog == null) authFingerDialog = new AuthFingerDialog(getActivity());
                    authFingerDialog.setOnShowListener((dialogInterface) -> {
                        mFingerPrintController.authenticate(authenticateResultCallback);
                    });
                    authFingerDialog.setOnDismissListener((dialog) -> {
                        mFingerPrintController.cancelAuth();
                    });
                    authFingerDialog.show();
                } else {
                    ToastSingleButtonDialog dialog = ToastSingleButtonDialog.getInstance(getActivity(), getResources().getString(R.string
                            .dialog_finger_setting));
                    dialog.setOnCancelClickListener(view -> {
                        FingerPrintController.openFingerPrintSettingPage(getActivity());
                        view.dismiss();
                    });
                }
            } else {
                //close fingerprint
                SharePrefUtil.putBoolean(ConstUtil.FINGERPRINT, false);
                fingerPrintSBV.setSwitch(false);
            }

        });
        aboutUsSBV.setOpenListener(() -> {
            Intent intent = new Intent(getActivity(), AboutUsActivity.class);
            startActivity(intent);
        });
        contactUsSBV.setOpenListener(() -> {
            ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData mClipData = ClipData.newPlainText("contact", "Nervos-Neuron");
            if (cm != null) {
                cm.setPrimaryClip(mClipData);
                Toast.makeText(getActivity(), R.string.copy_weixin_success, Toast.LENGTH_SHORT).show();
            }
        });
        forumsSBV.setOpenListener(() -> {
            SimpleWebActivity.gotoSimpleWeb(getActivity(), HttpUrls.NERVOS_FORUMS);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        initData();
    }

    AuthenticateResultCallback authenticateResultCallback = new AuthenticateResultCallback() {
        @Override
        public void onAuthenticationError(String errorMsg) {
            Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onAuthenticationSucceeded() {
            fingerPrintSBV.setSwitch(true);
            if (authFingerDialog != null && authFingerDialog.isShowing()) authFingerDialog.dismiss();
            SharePrefUtil.putBoolean(ConstUtil.FINGERPRINT, true);
            Toast.makeText(getContext(), getResources().getString(R.string.fingerprint_setting_sucess), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onAuthenticationFailed() {
            Toast.makeText(getContext(), getResources().getString(R.string.fingerprint_setting_failed), Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case Currency_Code:
                    currencySBV.setRightText(SharePrefUtil.getString(ConstUtil.CURRENCY, "CNY"));
                    break;
            }
        }
    }
}
