package alp.security.alpinkey.ui;

import android.content.Context;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import alp.alpinsense.webauthn.models.PublicKeyCredentialSource;
import alp.security.alpinkey.R;

public class CredentialAdapter extends RecyclerView.Adapter<CredentialAdapter.ViewHolder> {

    private List<PublicKeyCredentialSource> accounts;
    private Context context;
    private ViewHolderListener listener;
    private final static byte[] emptyUserId = new byte[4];

    public interface ViewHolderListener {
        void onClick(int position);

        void onDelete(View view, int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        View itemView;
        TextView rpName;
        TextView userName;
        ImageView deleteIcon;
        private ViewHolderListener listener;

        ViewHolder(@NonNull View itemView, ViewHolderListener listener) {
            super(itemView);
            this.listener = listener;

            this.itemView = itemView;
            rpName = itemView.findViewById(R.id.text_account_domain);
            userName = itemView.findViewById(R.id.text_account_user);
            deleteIcon = itemView.findViewById(R.id.img_deleteicon);

            itemView.setOnClickListener(this);
            deleteIcon.setOnClickListener(this);
        }

        @Override
        public void onClick(View view){
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (view.equals(deleteIcon)) {
                listener.onDelete(view, pos);
            } else {
                listener.onClick(pos);
            }
        }
    }

    public CredentialAdapter(Context ctx, List<PublicKeyCredentialSource> accounts, ViewHolderListener listener){
        context = ctx;
        this.accounts = accounts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CredentialAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.recycler_row_text,parent,false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull CredentialAdapter.ViewHolder holder, int position) {
        if (accounts == null || position < 0 || position >= accounts.size()) return;
        PublicKeyCredentialSource account = accounts.get(position);
        if (account == null) return;

        holder.rpName.setText(account.rpId != null ? account.rpId : "");
        String userName;
        if (account.userHandle != null && Arrays.equals(account.userHandle, emptyUserId)) {
            userName = holder.itemView.getContext().getString(R.string.credMan_u2fCredential);
        } else if (account.userDisplayName == null) {
            userName = account.userHandle != null ? Base64.encodeToString(account.userHandle, Base64.NO_WRAP) : "";
        } else {
            userName = account.userDisplayName;
        }
        holder.userName.setText(userName);
    }

    @Override
    public int getItemCount() {
        return accounts != null ? accounts.size() : 0;
    }
}
