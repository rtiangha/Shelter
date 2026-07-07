package net.typeblog.shelter.ui;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
        String pkg = mApps.remove(position);
        mPolicies.setApplicationHidden(pkg, true);
        ThawManager.onFrozen(this, pkg);
        mAdapter.notifyItemRemoved(position);
        if (mApps.isEmpty()) {
            finish();
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