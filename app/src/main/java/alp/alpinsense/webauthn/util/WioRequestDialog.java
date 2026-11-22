package alp.alpinsense.webauthn.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class WioRequestDialog extends DialogFragment {

    public static final int PROMPT_TIMEOUT_MS = 30000;
    private WioRequestDialog.PromptCallback mCallback;
    private String mMessage;
    private String mTitle;
    private Handler mainHandler;
    private Runnable timeout;
    private boolean resultDelivered;

    public static WioRequestDialog create(String title, String message, PromptCallback callback) {
        WioRequestDialog frag = new WioRequestDialog();

        frag.mTitle = title;
        frag.mMessage = message;
        frag.mCallback = callback;

        return frag;
    }

    @NotNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(mMessage).setTitle(mTitle);

        builder.setPositiveButton("Accept", (dialog, which) -> {
            mainHandler.removeCallbacks(timeout);
            deliverResult(true);
        });

        builder.setNegativeButton("Deny", (dialog, which) -> {
            mainHandler.removeCallbacks(timeout);
            deliverResult(false);
        });
        return builder.create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        if (mainHandler != null) mainHandler.removeCallbacks(timeout);
        deliverResult(false);
        super.onCancel(dialog);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (mainHandler != null) mainHandler.removeCallbacks(timeout);
        super.onDismiss(dialog);
    }

    public void show(@NotNull FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        FragmentManager fm = activity.getSupportFragmentManager();

        if (mainHandler == null) mainHandler = new Handler(activity.getMainLooper());
        mainHandler.post(() -> {
            try {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    this.show(fm, "WioRequestDialog");
                }
            } catch (Exception e) {
                Log.e("WioRequestDialog", "Failed to show request dialog", e);
            }
        });

        timeout = this::dismiss;
        mainHandler.postDelayed(timeout, PROMPT_TIMEOUT_MS);
    }

    @Override
    public void dismiss() {
        if (mainHandler != null) mainHandler.removeCallbacks(timeout);
        deliverResult(false);
        super.dismiss();
    }

    private void deliverResult(boolean result) {
        if (!resultDelivered && mCallback != null) {
            resultDelivered = true;
            mCallback.onResult(result);
        }
    }

    public abstract static class PromptCallback{
        public abstract void onResult(boolean result);
    }
}
