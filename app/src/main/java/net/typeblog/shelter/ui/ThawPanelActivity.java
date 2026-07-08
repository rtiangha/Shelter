package net.typeblog.shelter.ui;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.typeblog.shelter.R;
import net.typeblog.shelter.util.DevicePolicies;
import net.typeblog.shelter.util.ThawManager;

import java.util.ArrayList;
import java.util.List;

// The thaw panel: a floating list of currently-thawed apps. Swiping an app off the list
// freezes it immediately (no button, no undo -- relaunching from the shortcut is the undo).
// When the list becomes empty the panel closes, and ThawManager takes the notification down.
//
// Runs in the work profile, so it can freeze directly and needs no service binding.
public class ThawPanelActivity extends AppCompatActivity {
    private DevicePolicies mPolicies;
    private final List<String> mApps = new ArrayList<>();
    private Adapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        mPolicies = new DevicePolicies(this);

        // Only meaningful in the work profile; bail out defensively otherwise.
        if (!mPolicies.isProfileOwner()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_thaw_panel);
        setSupportActionBar(findViewById(R.id.thaw_panel_toolbar));
        getSupportActionBar().setTitle(R.string.thaw_panel_title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        RecyclerView list = findViewById(R.id.thaw_panel_recycler_view);
        mAdapter = new Adapter();
        list.setAdapter(mAdapter);
        list.setLayoutManager(new LinearLayoutManager(this));

        new ItemTouchHelper(new SwipeToFreezeCallback()).attachToRecyclerView(list);

        View freezeAll = findViewById(R.id.thaw_panel_freeze_all);
        freezeAll.setOnClickListener(v -> freezeAll());

        // We are edge-to-edge, so keep the floating pill clear of the gesture nav bar by
        // adding the bottom system-bar inset on top of its base margin.
        int baseMargin = ((ViewGroup.MarginLayoutParams) freezeAll.getLayoutParams()).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(freezeAll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = baseMargin + bars.bottom;
            v.setLayoutParams(lp);
            return insets;
        });

        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // State may have changed while we were backgrounded (auto-freeze, another panel, etc.).
        reload();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void reload() {
        mApps.clear();
        mApps.addAll(ThawManager.getThawedApps(this));
        if (mApps.isEmpty()) {
            finish();
            return;
        }
        mAdapter.notifyDataSetChanged();
    }

    private void freezeAt(int position) {
        String pkg = mApps.get(position);
        if (!mPolicies.setApplicationHidden(pkg, true)) {
            // Freeze failed (e.g. the app is an active device admin). Keep the row in
            // place -- restoring the swiped-away view -- and let the user know.
            Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show();
            mAdapter.notifyItemChanged(position);
            return;
        }
        mApps.remove(position);
        ThawManager.onFrozen(this, pkg);
        mAdapter.notifyItemRemoved(position);
        if (mApps.isEmpty()) {
            finish();
        }
    }

    // Freeze every app in one pass. Apps that refuse to freeze (e.g. active device admins)
    // stay in the list, exactly as a swipe on each would leave them; if any survive we
    // report it and keep the panel open, otherwise the emptied list closes it.
    private void freezeAll() {
        List<String> remaining = new ArrayList<>();
        for (String pkg : mApps) {
            if (mPolicies.setApplicationHidden(pkg, true)) {
                ThawManager.onFrozen(this, pkg);
            } else {
                remaining.add(pkg);
            }
        }

        mApps.clear();
        mApps.addAll(remaining);
        mAdapter.notifyDataSetChanged();

        if (mApps.isEmpty()) {
            finish();
        } else {
            Toast.makeText(this, R.string.freeze_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView mIcon;
            final TextView mTitle;

            Holder(View view) {
                super(view);
                mIcon = view.findViewById(R.id.thaw_item_icon);
                mTitle = view.findViewById(R.id.thaw_item_title);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.thaw_panel_item, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String pkg = mApps.get(position);
            try {
                holder.mTitle.setText(getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(pkg, 0)));
                holder.mIcon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                holder.mTitle.setText(pkg);
                holder.mIcon.setImageDrawable(getPackageManager().getDefaultActivityIcon());
            }
        }

        @Override
        public int getItemCount() {
            return mApps.size();
        }
    }

    // Swipe either direction to freeze; draws the freeze icon in the revealed track.
    private class SwipeToFreezeCallback extends ItemTouchHelper.SimpleCallback {
        private final Drawable mIcon;

        SwipeToFreezeCallback() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            mIcon = getDrawable(R.drawable.ic_freeze_tinted);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            freezeAt(viewHolder.getBindingAdapterPosition());
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            if (mIcon == null) return;

            View item = viewHolder.itemView;
            int size = mIcon.getIntrinsicHeight() > 0
                    ? Math.min(mIcon.getIntrinsicHeight(), item.getHeight() / 2)
                    : item.getHeight() / 3;
            int top = item.getTop() + (item.getHeight() - size) / 2;
            int margin = (item.getHeight() - size) / 2;

            if (dX > 0) {
                mIcon.setBounds(item.getLeft() + margin, top,
                        item.getLeft() + margin + size, top + size);
            } else if (dX < 0) {
                mIcon.setBounds(item.getRight() - margin - size, top,
                        item.getRight() - margin, top + size);
            } else {
                return;
            }
            mIcon.draw(c);
        }
    }
}