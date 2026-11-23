package alp.alpinsense.webauthn.util.database;


import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

import alp.alpinsense.webauthn.models.PublicKeyCredentialSource;

@Database(entities = {PublicKeyCredentialSource.class}, version = 2)
public abstract class CredentialDatabase extends RoomDatabase {
    private static CredentialDatabase INSTANCE;
    private static final String CREDENTIAL_DB_NAME = "credentialMetadata";

    static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1, 2) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE credentials ADD COLUMN privateKeyBytes BLOB");
            database.execSQL("ALTER TABLE credentials ADD COLUMN publicKeyBytes BLOB");
        }
    };

    public static CredentialDatabase getDatabase(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(), CredentialDatabase.class, CREDENTIAL_DB_NAME)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build();
        }
        return INSTANCE;
    }

    public abstract CredentialDao credentialDao();
}
