package com.kira.followlens.ui;

import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;

import com.kira.followlens.R;
import com.kira.followlens.data.EdgeRow;
import com.kira.followlens.data.FollowLensDao;
import com.kira.followlens.data.ListKind;

import java.util.List;

/** The list the dashboard is currently showing. */
public enum ListView {

    FOLLOWERS(R.string.followers),
    FOLLOWING(R.string.following),
    MUTUALS(R.string.mutuals),
    NOT_FOLLOWING_BACK(R.string.not_following_back),
    FANS(R.string.fans);

    private final int labelRes;

    ListView(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    @StringRes
    public int labelRes() {
        return labelRes;
    }

    public LiveData<List<EdgeRow>> query(FollowLensDao dao, String accountId) {
        switch (this) {
            case FOLLOWERS:
                return dao.edges(accountId, ListKind.FOLLOWER);
            case FOLLOWING:
                return dao.edges(accountId, ListKind.FOLLOWING);
            case MUTUALS:
                return dao.mutuals(accountId);
            case NOT_FOLLOWING_BACK:
                return dao.notFollowingBack(accountId);
            case FANS:
                return dao.fans(accountId);
            default:
                throw new IllegalStateException("unhandled list view: " + this);
        }
    }
}
