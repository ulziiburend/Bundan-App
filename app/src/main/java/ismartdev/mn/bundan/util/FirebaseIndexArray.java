package ismartdev.mn.bundan.util;

/**
 * Created by Ulzii on 11/13/2016.
 */


import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class FirebaseIndexArray extends FirebaseArray {
    private static final String TAG = FirebaseIndexArray.class.getSimpleName();

    private Query mQuery;
    private Map<Query, ValueEventListener> mRefs = new HashMap<>();
    private List<DataSnapshot> mDataSnapshots = new ArrayList<>();
    private OnChangedListener mListener;

    FirebaseIndexArray(Query keyRef, Query dataRef) {
        super(keyRef);
        mQuery = dataRef;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        Set<Query> refs = new HashSet<>(mRefs.keySet());
        for (Query ref : refs) {
            ref.removeEventListener(mRefs.remove(ref));
        }
    }

    @Override
    public int getCount() {
        return mDataSnapshots.size();
    }

    @Override
    public DataSnapshot getItem(int index) {
        return mDataSnapshots.get(index);
    }

    private int getIndexForKey(String key) {
        int dataCount = getCount();
        int index = 0;
        for (int keyIndex = 0; index < dataCount; keyIndex++) {
            String superKey = super.getItem(keyIndex).getKey();
            if (key.equals(superKey)) {
                break;
            } else if (getItem(index).getKey().equals(superKey)) {
                index++;
            }
        }
        return index;
    }

    private boolean isMatch(int index, String key) {
        return index >= 0 && index < getCount() && getItem(index).getKey().equals(key);
    }

    @Override
    public void onChildAdded(DataSnapshot keySnapshot, String previousChildKey) {
        super.setOnChangedListener(null);
        super.onChildAdded(keySnapshot, previousChildKey);
        super.setOnChangedListener(mListener);

        Query ref = mQuery.getRef().child(keySnapshot.getKey());
        mRefs.put(ref, ref.addValueEventListener(new DataRefListener()));
    }

    @Override
    public void onChildChanged(DataSnapshot snapshot, String previousChildKey) {
        super.setOnChangedListener(null);
        super.onChildChanged(snapshot, previousChildKey);
        super.setOnChangedListener(mListener);
    }

    @Override
    public void onChildRemoved(DataSnapshot keySnapshot) {
        String key = keySnapshot.getKey();
        int index = getIndexForKey(key);
        mQuery.getRef().child(key).removeEventListener(mRefs.remove(mQuery.getRef().child(key)));

        super.setOnChangedListener(null);
        super.onChildRemoved(keySnapshot);
        super.setOnChangedListener(mListener);

        if (isMatch(index, key)) {
            mDataSnapshots.remove(index);
            notifyChangedListeners(OnChangedListener.EventType.REMOVED, index);
        }
    }

    @Override
    public void onChildMoved(DataSnapshot keySnapshot, String previousChildKey) {
        String key = keySnapshot.getKey();
        int oldIndex = getIndexForKey(key);

        super.setOnChangedListener(null);
        super.onChildMoved(keySnapshot, previousChildKey);
        super.setOnChangedListener(mListener);

        if (isMatch(oldIndex, key)) {
            DataSnapshot snapshot = mDataSnapshots.remove(oldIndex);
            int newIndex = getIndexForKey(key);
            mDataSnapshots.add(newIndex, snapshot);
            notifyChangedListeners(OnChangedListener.EventType.MOVED, newIndex, oldIndex);
        }
    }

    @Override
    public void onCancelled(DatabaseError error) {
        Log.e(TAG, "A fatal error occurred retrieving the necessary keys to populate your adapter.");
        super.onCancelled(error);
    }

    @Override
    public void setOnChangedListener(OnChangedListener listener) {
        super.setOnChangedListener(listener);
        mListener = listener;
    }

    private class DataRefListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
            String key = snapshot.getKey();
            int index = getIndexForKey(key);

            if (snapshot.getValue() != null) {
                if (!isMatch(index, key)) {
                    mDataSnapshots.add(index, snapshot);
                    notifyChangedListeners(OnChangedListener.EventType.ADDED, index);
                } else {
                    mDataSnapshots.set(index, snapshot);
                    notifyChangedListeners(OnChangedListener.EventType.CHANGED, index);
                }
            } else {
                Log.w(TAG, "Key not found at ref: " + snapshot.getRef());
                if (isMatch(index, key)) {
                    mDataSnapshots.remove(index);
                    notifyChangedListeners(OnChangedListener.EventType.REMOVED, index);
                }
            }
        }

        @Override
        public void onCancelled(DatabaseError error) {
            notifyCancelledListeners(error);
        }
    }
}