package com.example.noteapp.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.noteapp.domain.model.Category;
import com.example.noteapp.domain.model.Note;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class NoteDao_Impl implements NoteDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Note> __insertionAdapterOfNote;

  private final Converters __converters = new Converters();

  private final EntityInsertionAdapter<Category> __insertionAdapterOfCategory;

  private final EntityDeletionOrUpdateAdapter<Note> __deletionAdapterOfNote;

  private final EntityDeletionOrUpdateAdapter<Category> __deletionAdapterOfCategory;

  private final EntityDeletionOrUpdateAdapter<Note> __updateAdapterOfNote;

  private final SharedSQLiteStatement __preparedStmtOfEmptyTrash;

  public NoteDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfNote = new EntityInsertionAdapter<Note>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `notes` (`id`,`title`,`content`,`timestamp`,`color`,`categoryId`,`isPinned`,`isArchived`,`isDeleted`,`isLocked`,`reminderTime`,`attachments`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Note entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getContent());
        statement.bindLong(4, entity.getTimestamp());
        statement.bindLong(5, entity.getColor());
        if (entity.getCategoryId() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getCategoryId());
        }
        final int _tmp = entity.isPinned() ? 1 : 0;
        statement.bindLong(7, _tmp);
        final int _tmp_1 = entity.isArchived() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
        final int _tmp_2 = entity.isDeleted() ? 1 : 0;
        statement.bindLong(9, _tmp_2);
        final int _tmp_3 = entity.isLocked() ? 1 : 0;
        statement.bindLong(10, _tmp_3);
        if (entity.getReminderTime() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getReminderTime());
        }
        final String _tmp_4 = __converters.fromStringList(entity.getAttachments());
        statement.bindString(12, _tmp_4);
      }
    };
    this.__insertionAdapterOfCategory = new EntityInsertionAdapter<Category>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `categories` (`id`,`name`,`color`) VALUES (nullif(?, 0),?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Category entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindLong(3, entity.getColor());
      }
    };
    this.__deletionAdapterOfNote = new EntityDeletionOrUpdateAdapter<Note>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `notes` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Note entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__deletionAdapterOfCategory = new EntityDeletionOrUpdateAdapter<Category>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `categories` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Category entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfNote = new EntityDeletionOrUpdateAdapter<Note>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `notes` SET `id` = ?,`title` = ?,`content` = ?,`timestamp` = ?,`color` = ?,`categoryId` = ?,`isPinned` = ?,`isArchived` = ?,`isDeleted` = ?,`isLocked` = ?,`reminderTime` = ?,`attachments` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Note entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getContent());
        statement.bindLong(4, entity.getTimestamp());
        statement.bindLong(5, entity.getColor());
        if (entity.getCategoryId() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getCategoryId());
        }
        final int _tmp = entity.isPinned() ? 1 : 0;
        statement.bindLong(7, _tmp);
        final int _tmp_1 = entity.isArchived() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
        final int _tmp_2 = entity.isDeleted() ? 1 : 0;
        statement.bindLong(9, _tmp_2);
        final int _tmp_3 = entity.isLocked() ? 1 : 0;
        statement.bindLong(10, _tmp_3);
        if (entity.getReminderTime() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getReminderTime());
        }
        final String _tmp_4 = __converters.fromStringList(entity.getAttachments());
        statement.bindString(12, _tmp_4);
        statement.bindLong(13, entity.getId());
      }
    };
    this.__preparedStmtOfEmptyTrash = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM notes WHERE isDeleted = 1";
        return _query;
      }
    };
  }

  @Override
  public Object insertNote(final Note note, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfNote.insertAndReturnId(note);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertCategory(final Category category,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfCategory.insertAndReturnId(category);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteNote(final Note note, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfNote.handle(note);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteCategory(final Category category,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfCategory.handle(category);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateNote(final Note note, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfNote.handle(note);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object emptyTrash(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfEmptyTrash.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfEmptyTrash.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Note>> getActiveNotes() {
    final String _sql = "SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY isPinned DESC, timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notes"}, new Callable<List<Note>>() {
      @Override
      @NonNull
      public List<Note> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsArchived = CursorUtil.getColumnIndexOrThrow(_cursor, "isArchived");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsLocked = CursorUtil.getColumnIndexOrThrow(_cursor, "isLocked");
          final int _cursorIndexOfReminderTime = CursorUtil.getColumnIndexOrThrow(_cursor, "reminderTime");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<Note> _result = new ArrayList<Note>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Note _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            final Long _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsPinned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _tmpIsPinned = _tmp != 0;
            final boolean _tmpIsArchived;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsArchived);
            _tmpIsArchived = _tmp_1 != 0;
            final boolean _tmpIsDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp_2 != 0;
            final boolean _tmpIsLocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsLocked);
            _tmpIsLocked = _tmp_3 != 0;
            final Long _tmpReminderTime;
            if (_cursor.isNull(_cursorIndexOfReminderTime)) {
              _tmpReminderTime = null;
            } else {
              _tmpReminderTime = _cursor.getLong(_cursorIndexOfReminderTime);
            }
            final List<String> _tmpAttachments;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfAttachments);
            _tmpAttachments = __converters.toStringList(_tmp_4);
            _item = new Note(_tmpId,_tmpTitle,_tmpContent,_tmpTimestamp,_tmpColor,_tmpCategoryId,_tmpIsPinned,_tmpIsArchived,_tmpIsDeleted,_tmpIsLocked,_tmpReminderTime,_tmpAttachments);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Note>> getArchivedNotes() {
    final String _sql = "SELECT * FROM notes WHERE isArchived = 1 AND isDeleted = 0 ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notes"}, new Callable<List<Note>>() {
      @Override
      @NonNull
      public List<Note> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsArchived = CursorUtil.getColumnIndexOrThrow(_cursor, "isArchived");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsLocked = CursorUtil.getColumnIndexOrThrow(_cursor, "isLocked");
          final int _cursorIndexOfReminderTime = CursorUtil.getColumnIndexOrThrow(_cursor, "reminderTime");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<Note> _result = new ArrayList<Note>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Note _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            final Long _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsPinned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _tmpIsPinned = _tmp != 0;
            final boolean _tmpIsArchived;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsArchived);
            _tmpIsArchived = _tmp_1 != 0;
            final boolean _tmpIsDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp_2 != 0;
            final boolean _tmpIsLocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsLocked);
            _tmpIsLocked = _tmp_3 != 0;
            final Long _tmpReminderTime;
            if (_cursor.isNull(_cursorIndexOfReminderTime)) {
              _tmpReminderTime = null;
            } else {
              _tmpReminderTime = _cursor.getLong(_cursorIndexOfReminderTime);
            }
            final List<String> _tmpAttachments;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfAttachments);
            _tmpAttachments = __converters.toStringList(_tmp_4);
            _item = new Note(_tmpId,_tmpTitle,_tmpContent,_tmpTimestamp,_tmpColor,_tmpCategoryId,_tmpIsPinned,_tmpIsArchived,_tmpIsDeleted,_tmpIsLocked,_tmpReminderTime,_tmpAttachments);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Note>> getTrashNotes() {
    final String _sql = "SELECT * FROM notes WHERE isDeleted = 1 ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notes"}, new Callable<List<Note>>() {
      @Override
      @NonNull
      public List<Note> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsArchived = CursorUtil.getColumnIndexOrThrow(_cursor, "isArchived");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsLocked = CursorUtil.getColumnIndexOrThrow(_cursor, "isLocked");
          final int _cursorIndexOfReminderTime = CursorUtil.getColumnIndexOrThrow(_cursor, "reminderTime");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<Note> _result = new ArrayList<Note>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Note _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            final Long _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsPinned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _tmpIsPinned = _tmp != 0;
            final boolean _tmpIsArchived;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsArchived);
            _tmpIsArchived = _tmp_1 != 0;
            final boolean _tmpIsDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp_2 != 0;
            final boolean _tmpIsLocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsLocked);
            _tmpIsLocked = _tmp_3 != 0;
            final Long _tmpReminderTime;
            if (_cursor.isNull(_cursorIndexOfReminderTime)) {
              _tmpReminderTime = null;
            } else {
              _tmpReminderTime = _cursor.getLong(_cursorIndexOfReminderTime);
            }
            final List<String> _tmpAttachments;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfAttachments);
            _tmpAttachments = __converters.toStringList(_tmp_4);
            _item = new Note(_tmpId,_tmpTitle,_tmpContent,_tmpTimestamp,_tmpColor,_tmpCategoryId,_tmpIsPinned,_tmpIsArchived,_tmpIsDeleted,_tmpIsLocked,_tmpReminderTime,_tmpAttachments);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Note>> getNotesByCategory(final long categoryId) {
    final String _sql = "SELECT * FROM notes WHERE categoryId = ? AND isDeleted = 0 AND isArchived = 0 ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, categoryId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notes"}, new Callable<List<Note>>() {
      @Override
      @NonNull
      public List<Note> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsArchived = CursorUtil.getColumnIndexOrThrow(_cursor, "isArchived");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsLocked = CursorUtil.getColumnIndexOrThrow(_cursor, "isLocked");
          final int _cursorIndexOfReminderTime = CursorUtil.getColumnIndexOrThrow(_cursor, "reminderTime");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final List<Note> _result = new ArrayList<Note>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Note _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            final Long _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsPinned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _tmpIsPinned = _tmp != 0;
            final boolean _tmpIsArchived;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsArchived);
            _tmpIsArchived = _tmp_1 != 0;
            final boolean _tmpIsDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp_2 != 0;
            final boolean _tmpIsLocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsLocked);
            _tmpIsLocked = _tmp_3 != 0;
            final Long _tmpReminderTime;
            if (_cursor.isNull(_cursorIndexOfReminderTime)) {
              _tmpReminderTime = null;
            } else {
              _tmpReminderTime = _cursor.getLong(_cursorIndexOfReminderTime);
            }
            final List<String> _tmpAttachments;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfAttachments);
            _tmpAttachments = __converters.toStringList(_tmp_4);
            _item = new Note(_tmpId,_tmpTitle,_tmpContent,_tmpTimestamp,_tmpColor,_tmpCategoryId,_tmpIsPinned,_tmpIsArchived,_tmpIsDeleted,_tmpIsLocked,_tmpReminderTime,_tmpAttachments);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getNoteById(final long id, final Continuation<? super Note> $completion) {
    final String _sql = "SELECT * FROM notes WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Note>() {
      @Override
      @Nullable
      public Note call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "categoryId");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfIsArchived = CursorUtil.getColumnIndexOrThrow(_cursor, "isArchived");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfIsLocked = CursorUtil.getColumnIndexOrThrow(_cursor, "isLocked");
          final int _cursorIndexOfReminderTime = CursorUtil.getColumnIndexOrThrow(_cursor, "reminderTime");
          final int _cursorIndexOfAttachments = CursorUtil.getColumnIndexOrThrow(_cursor, "attachments");
          final Note _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            final Long _tmpCategoryId;
            if (_cursor.isNull(_cursorIndexOfCategoryId)) {
              _tmpCategoryId = null;
            } else {
              _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            }
            final boolean _tmpIsPinned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _tmpIsPinned = _tmp != 0;
            final boolean _tmpIsArchived;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsArchived);
            _tmpIsArchived = _tmp_1 != 0;
            final boolean _tmpIsDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp_2 != 0;
            final boolean _tmpIsLocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsLocked);
            _tmpIsLocked = _tmp_3 != 0;
            final Long _tmpReminderTime;
            if (_cursor.isNull(_cursorIndexOfReminderTime)) {
              _tmpReminderTime = null;
            } else {
              _tmpReminderTime = _cursor.getLong(_cursorIndexOfReminderTime);
            }
            final List<String> _tmpAttachments;
            final String _tmp_4;
            _tmp_4 = _cursor.getString(_cursorIndexOfAttachments);
            _tmpAttachments = __converters.toStringList(_tmp_4);
            _result = new Note(_tmpId,_tmpTitle,_tmpContent,_tmpTimestamp,_tmpColor,_tmpCategoryId,_tmpIsPinned,_tmpIsArchived,_tmpIsDeleted,_tmpIsLocked,_tmpReminderTime,_tmpAttachments);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Category>> getAllCategories() {
    final String _sql = "SELECT * FROM categories ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"categories"}, new Callable<List<Category>>() {
      @Override
      @NonNull
      public List<Category> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final List<Category> _result = new ArrayList<Category>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Category _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final int _tmpColor;
            _tmpColor = _cursor.getInt(_cursorIndexOfColor);
            _item = new Category(_tmpId,_tmpName,_tmpColor);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
