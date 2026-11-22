package alp.security.alpinkey.ui.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.net.Uri;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt.CryptoObject;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import alp.alpinsense.webauthn.models.PublicKeyCredentialSource;
import alp.alpinsense.webauthn.util.CredentialBackupManager;
import alp.security.alpinkey.MainActivity;
import alp.security.alpinkey.R;
import alp.security.alpinkey.ui.AccountInfoDialog;
import alp.security.alpinkey.ui.CredentialAdapter;

import alp.alpinsense.webauthn.util.WioBiometricPrompt.PromptCallback;
import alp.security.alpinkey.utils.FirebaseManager;

import static alp.security.alpinkey.utils.FirebaseManager.EVENT_NUMBEROFACCOUNTS;
import static alp.security.alpinkey.utils.FirebaseManager.EVENT_ONPOPUP;

public class CredentialFragment extends Fragment {

    FloatingActionButton mDeleteAll;
    FloatingActionButton mResetPin;
    FloatingActionButton mHardwareBacked;
    FloatingActionButton mBackup;
    FloatingActionButton mRestore;
    AlertDialog mHardwareDialog;
    AlertDialog mPinDialog;
    RecyclerView mCredentials;
    View mEmptyStateLayout;
    CredentialAdapter mCredentialAdapter;
    Dialog mClientPinDialog;
    AccountInfoDialog mAccountInfoDialog;
    List<PublicKeyCredentialSource> credentialList;

    private String pendingBackupPassword = null;
    private byte[] pendingRestoreData = null;

    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null && pendingBackupPassword != null) {
                    try {
                        byte[] encryptedData = CredentialBackupManager.createEncryptedBackup(credentialList, pendingBackupPassword);
                        try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                os.write(encryptedData);
                                os.flush();
                                Toast.makeText(getActivity(), getString(R.string.credMan_backupSuccess), Toast.LENGTH_LONG).show();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to create backup", e);
                        Toast.makeText(getActivity(), getString(R.string.credMan_backupFailed, e.getMessage()), Toast.LENGTH_LONG).show();
                    } finally {
                        pendingBackupPassword = null;
                    }
                }
            });

    private final ActivityResultLauncher<String[]> selectRestoreLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                baos.write(buffer, 0, len);
                            }
                        }
                        pendingRestoreData = baos.toByteArray();
                        promptRestorePassword();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to read backup file", e);
                        Toast.makeText(getActivity(), getString(R.string.credMan_restoreFailed), Toast.LENGTH_LONG).show();
                    }
                }
            });

    static final String TAG = "AlpinKey|CredentialFragment";

    CredentialAdapter.ViewHolderListener listener = new CredentialAdapter.ViewHolderListener() {
        @Override
        public void onClick(int position) {
            onSelect(position);
        }

        @Override
        public void onDelete(View view, int position) {
            final PromptCallback callback = new PromptCallback(true) {
                @Override
                public void onResult(boolean result, CryptoObject cryptoObject) {
                    if (!result) {
                        Toast.makeText(getActivity(),getString(R.string.credMan_operationCanceled),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (position >= 0 && position < credentialList.size()) {
                        final PublicKeyCredentialSource credential = credentialList.remove(position);
                        mCredentialAdapter.notifyItemRemoved(position);
                        mCredentialAdapter.notifyItemRangeChanged(position, credentialList.size());
                        updateEmptyStateVisibility();

                        Toast.makeText(getActivity(),
                                        getString(R.string.credMan_removeSuccess,
                                                credential.rpId, credential.userDisplayName),
                                        Toast.LENGTH_LONG).show();
                    }
                }
            };

            if ((MainActivity.mAuthenticator != null) && (MainActivity.isOnForeground())) {
                MainActivity.mAuthenticator.deleteCredential(getActivity(),
                        credentialList.get(position),
                        callback);
            }
        }
    };

    View.OnClickListener listenerOnDeleteAll = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final PromptCallback callback = new PromptCallback(true) {
                @Override
                public void onResult(boolean result, CryptoObject cryptoObject) {
                    if (!result) {
                        Toast.makeText(getActivity(),
                                        getString(R.string.credMan_operationCanceled),
                                        Toast.LENGTH_LONG).show();
                        return;
                    }

                    credentialList.clear();
                    mCredentialAdapter.notifyDataSetChanged();
                    updateEmptyStateVisibility();

                    Toast.makeText(getActivity(),
                            getString(R.string.credMan_resetSuccess),
                            Toast.LENGTH_LONG).show();
                }
            };

            if ((MainActivity.mAuthenticator != null) && (MainActivity.isOnForeground())) {
                MainActivity.mAuthenticator.deleteAllCredentials(getActivity(), callback);
            }
        }
    };

    View.OnClickListener listenerResetPin = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case (DialogInterface.BUTTON_POSITIVE): {
                            // We got a call to update (set / change) the PIN - so fire the popUp
                            onChangePinPopUp();
                            break;
                        }
                        case (DialogInterface.BUTTON_NEGATIVE): {
                            // We got a call to reset the PIN of the authenticator so we do so
                            final PromptCallback callback = new PromptCallback(true) {
                                @Override
                                public void onResult(boolean result, CryptoObject cryptoObject) {
                                    if (result) {
                                        Toast.makeText(getActivity(),
                                                getString(R.string.credMan_pinResetSuccess),
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(getActivity(),
                                                getString(R.string.credMan_pinResetCanceled),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            };

                            if ((MainActivity.mAuthenticator != null) && (MainActivity.isOnForeground())) {
                                MainActivity.mAuthenticator.resetPin(getActivity(), callback);
                            }
                            break;
                        }
                        default:
                            // Nothing to do here
                    }
                }
            };

            String body = getString(R.string.dialog_body_clientpin);
            if (MainActivity.mAuthenticator != null && MainActivity.mAuthenticator.isPinSet()) {
                body += getString(R.string.dialog_status_clientpin_set);
            } else {
                body += getString(R.string.dialog_status_clientpin_not_set);
            }
            mPinDialog = new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                    .setTitle(getString(R.string.dialog_title_clientpin))
                    .setCancelable(true)
                    .setIcon(R.drawable.ic_pin_pad_white)
                    .setMessage(body)
                    .setNegativeButton(getString(R.string.dialog_btn_factoryreset_clientpin), listener)
                    .setPositiveButton(getString(R.string.dialog_btn_changepin_clientpin), listener)
                    .show();
        }
    };

    View.OnClickListener listenerHardwareBacked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mHardwareDialog.show();
        }
    };

    View.OnClickListener listenerBackup = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (credentialList == null || credentialList.isEmpty()) {
                Toast.makeText(getActivity(), getString(R.string.credMan_noCredentialsToBackup), Toast.LENGTH_LONG).show();
                return;
            }
            promptBackupPassword();
        }
    };

    View.OnClickListener listenerRestore = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            selectRestoreLauncher.launch(new String[]{"*/*"});
        }
    };

    private void promptBackupPassword() {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password");

        new AlertDialog.Builder(requireContext(), R.style.DialogTheme)
                .setTitle(getString(R.string.credMan_backupTitle))
                .setMessage(getString(R.string.credMan_backupPasswordPrompt))
                .setView(input)
                .setPositiveButton("Backup", (dialog, which) -> {
                    String password = input.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(getActivity(), "Password cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pendingBackupPassword = password;
                    createBackupLauncher.launch("alpinkey_credentials_backup.json");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptRestorePassword() {
        if (pendingRestoreData == null) return;

        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password");

        new AlertDialog.Builder(requireContext(), R.style.DialogTheme)
                .setTitle(getString(R.string.credMan_restoreTitle))
                .setMessage(getString(R.string.credMan_restorePasswordPrompt))
                .setView(input)
                .setPositiveButton("Restore", (dialog, which) -> {
                    String password = input.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(getActivity(), "Password cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        List<PublicKeyCredentialSource> restored = CredentialBackupManager.decryptBackup(pendingRestoreData, password);
                        int restoredCount = CredentialBackupManager.restoreCredentialsToDatabase(requireContext(), restored);
                        refreshCredentialsList();
                        Toast.makeText(getActivity(), getString(R.string.credMan_restoreSuccess, restoredCount), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Restore failed", e);
                        Toast.makeText(getActivity(), getString(R.string.credMan_restoreFailed), Toast.LENGTH_LONG).show();
                    } finally {
                        pendingRestoreData = null;
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> pendingRestoreData = null)
                .show();
    }

    private void updateEmptyStateVisibility() {
        if (mEmptyStateLayout != null) {
            if (credentialList == null || credentialList.isEmpty()) {
                mEmptyStateLayout.setVisibility(View.VISIBLE);
                mCredentials.setVisibility(View.GONE);
            } else {
                mEmptyStateLayout.setVisibility(View.GONE);
                mCredentials.setVisibility(View.VISIBLE);
            }
        }
    }

    private void refreshCredentialsList() {
        if (MainActivity.mAuthenticator != null) {
            credentialList.clear();
            credentialList.addAll(MainActivity.mAuthenticator.getAllCredentials());
            if (mCredentialAdapter != null) {
                mCredentialAdapter.notifyDataSetChanged();
            }
            updateEmptyStateVisibility();
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_credential, container, false);

        mCredentials = root.findViewById(R.id.listview_credentials);
        mEmptyStateLayout = root.findViewById(R.id.layout_empty_state);
        mDeleteAll = root.findViewById(R.id.button_deleteall);
        mResetPin = root.findViewById(R.id.button_resetPin);
        mHardwareBacked = root.findViewById(R.id.button_hardwareBacked);
        mBackup = root.findViewById(R.id.button_backup);
        mRestore = root.findViewById(R.id.button_restore);
        mClientPinDialog = new Dialog(Objects.requireNonNull(getActivity()));

        mDeleteAll.setOnClickListener(listenerOnDeleteAll);
        mResetPin.setOnClickListener(listenerResetPin);
        mHardwareBacked.setOnClickListener(listenerHardwareBacked);
        if (mBackup != null) mBackup.setOnClickListener(listenerBackup);
        if (mRestore != null) mRestore.setOnClickListener(listenerRestore);

        mHardwareDialog = new AlertDialog.Builder(this.getContext(), R.style.DialogTheme)
                .setMessage(R.string.credMan_hardwareBacked)
                .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create();

        SharedPreferences appPreferences = this.getContext().getSharedPreferences(
                                                                    MainActivity.PREFERENCES,
                                                                    Context.MODE_PRIVATE);
        String hasHwStorage = appPreferences.getString(MainActivity.HAS_HW_STORAGE, "false");
        if (!hasHwStorage.equals("true")){
            mHardwareBacked.hide();
        }

        try {
            if (MainActivity.mAuthenticator != null) {
                credentialList = MainActivity.mAuthenticator.getAllCredentials();
            }
            if (credentialList == null) {
                credentialList = new ArrayList<>();
            }
            FirebaseManager.sendLogEvent(EVENT_NUMBEROFACCOUNTS, credentialList.size());
        } catch (Exception e){
            Log.e(TAG, "Exception caught on retrieving credentials: " + e);
            credentialList = new ArrayList<>();
        }

        mCredentialAdapter = new CredentialAdapter(getActivity(), credentialList, listener);

        mCredentials.setAdapter(mCredentialAdapter);
        mCredentials.setLayoutManager(new LinearLayoutManager(getActivity()));

        updateEmptyStateVisibility();

        return root;
    }

    boolean onSelect(int position){
        if (position >= 0 && position < credentialList.size()) {
            mAccountInfoDialog = new AccountInfoDialog(getActivity(), credentialList.get(position));
            return true;
        }
        return false;
    }

    private void onChangePinPopUp() {
        FirebaseManager.sendLogEvent(EVENT_ONPOPUP,null);
        if (MainActivity.mAuthenticator != null && MainActivity.isOnForeground()) {
            boolean isPinSet = MainActivity.mAuthenticator.isPinSet();
            if (isPinSet) {
                mClientPinDialog.setContentView(R.layout.popup_changeclientpinview);
            } else {
                mClientPinDialog.setContentView(R.layout.popup_setclientpinview);
            }

            mClientPinDialog.show();
            EditText oldPinText = mClientPinDialog.findViewById(R.id.old_pin);
            EditText newPinText = mClientPinDialog.findViewById(R.id.new_pin);
            EditText repeatedPinText = mClientPinDialog.findViewById(R.id.repeated_pin);
            Button btnChangePin = mClientPinDialog.findViewById(R.id.btn_changepin);

            final Button.OnClickListener btnListener = new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String pinCandidate = newPinText.getText().toString();
                    String oldPin;
                    if (isPinSet) {
                        oldPin = oldPinText.getText().toString();
                    } else {
                        oldPin = null;
                    }

                    // Minimal form verification prior to submitting job
                    if (!pinCandidate.equals(repeatedPinText.getText().toString())) {
                        Toast.makeText(getActivity(),
                                getString(R.string.credMan_changeClientPinNoMatch),
                                Toast.LENGTH_LONG).show();
                        newPinText.getText().clear();
                        if (isPinSet) oldPinText.getText().clear();
                        repeatedPinText.getText().clear();
                        return;
                    }

                    // Minimal form verification prior to submitting job
                    if (pinCandidate.length() < 4) {
                        Toast.makeText(getActivity(),
                                getString(R.string.credMan_changeClientPinTooShort),
                                Toast.LENGTH_LONG).show();
                        newPinText.getText().clear();
                        if (isPinSet) oldPinText.getText().clear();
                        repeatedPinText.getText().clear();
                        return;
                    }

                    final PromptCallback callback = new PromptCallback(true) {
                        @Override
                        public void onResult(boolean result, CryptoObject cryptoObject) {
                            if (result) {
                                Toast.makeText(getActivity(),
                                        getString(R.string.credMan_changeClientPinSuccess),
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getActivity(),
                                        getString(R.string.credMan_changeClientPinCanceled),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    };

                    // Now the job is belonging to the Authenticator internals
                    MainActivity.mAuthenticator.selfSetPin(getActivity(), pinCandidate, oldPin, callback);

                    // Once we are done we close the dialog and return to previous screen
                    mClientPinDialog.dismiss();
                }
            };

            btnChangePin.setOnClickListener(btnListener);
        }
    }

    private void onCleanUpDialogs() {
        if (mHardwareDialog != null) mHardwareDialog.dismiss();
        if (mPinDialog != null) mPinDialog.dismiss();
        if (mClientPinDialog != null) mClientPinDialog.dismiss();
        if (mAccountInfoDialog != null) mAccountInfoDialog.dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        onCleanUpDialogs();
    }
}
