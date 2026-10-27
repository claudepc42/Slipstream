package us.shandian.giga.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.imported.ImportedFileEntity;
import org.schabi.newpipe.util.WatchProgressHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class ImportedFilesAdapter extends RecyclerView.Adapter<ImportedFilesAdapter.ViewHolder> {

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final List<ImportedFileEntity> mItems = new ArrayList<>();
    private final CompositeDisposable mDisposables = new CompositeDisposable();

    public ImportedFilesAdapter(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    public void setItems(List<ImportedFileEntity> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(mInflater.inflate(R.layout.mission_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ImportedFileEntity entity = mItems.get(position);

        holder.name.setText(entity.getTitle());
        holder.size.setText(entity.getUploader() != null ? entity.getUploader() : "");
        holder.status.setText("");
        holder.date.setText("");
        holder.icon.setVisibility(View.GONE);
        holder.thumbnail.setVisibility(View.VISIBLE);
        holder.watchProgressBar.setVisibility(View.GONE);
        holder.progressOverlay.setVisibility(View.GONE);

        if (entity.getThumbnailUrl() != null) {
            CoilHelper.INSTANCE.loadThumbnail(holder.thumbnail, entity.getThumbnailUrl());
        } else {
            holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        if (entity.getVideoId() != null) {
            final String url = "https://www.youtube.com/watch?v=" + entity.getVideoId();
            mDisposables.add(
                WatchProgressHelper.INSTANCE.loadProgress(mContext, url)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        pair -> {
                            if (pair != null && pair.getSecond() > 0) {
                                float fraction = (float) pair.getFirst() / (pair.getSecond() * 1000f);
                                fraction = Math.max(0f, Math.min(1f, fraction));
                                if (fraction > 0.03f && fraction < 0.97f) {
                                    holder.watchProgressBar.setVisibility(View.VISIBLE);
                                    holder.watchProgressBar.setScaleX(fraction);
                                }
                            }
                        },
                        error -> { /* ignore */ }
                    )
            );
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(entity.getFileUri()), "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            mContext.startActivity(Intent.createChooser(intent, entity.getTitle()));
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public void onDestroy() {
        mDisposables.clear();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView size;
        final TextView status;
        final TextView date;
        final ImageView icon;
        final ImageView thumbnail;
        final View watchProgressBar;
        final View progressOverlay;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.item_name);
            size = itemView.findViewById(R.id.item_size);
            status = itemView.findViewById(R.id.item_status);
            date = itemView.findViewById(R.id.item_date);
            icon = itemView.findViewById(R.id.item_icon);
            thumbnail = itemView.findViewById(R.id.item_thumbnail);
            watchProgressBar = itemView.findViewById(R.id.item_watch_progress);
            watchProgressBar.setPivotX(0f);
            progressOverlay = itemView.findViewById(R.id.item_progress_overlay);
        }
    }
}
