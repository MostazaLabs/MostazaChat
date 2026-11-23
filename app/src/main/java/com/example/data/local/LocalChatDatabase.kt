package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_profiles")
data class LocalProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String?,
    val defaultMyName: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "local_chats",
    indices = [Index(value = ["userId", "pinned", "updatedAtMillis"])]
)
data class LocalChatEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val contactName: String,
    val groupChat: Boolean,
    val statusText: String,
    val online: Boolean,
    val verified: Boolean,
    val avatarEmoji: String,
    val avatarColorHex: String,
    val avatarImageBase64: String?,
    val wallpaperTheme: String,
    val unreadCount: Long,
    val pinned: Boolean,
    val lastMessagePreview: String,
    val lastMessageTime: String,
    val lastMessageSender: String,
    val lastMessageTick: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "local_messages",
    foreignKeys = [
        ForeignKey(
            entity = LocalChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId", "orderIndex"])]
)
data class LocalMessageEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val chatId: String,
    val senderRole: String,
    val senderName: String,
    val text: String,
    val messageType: String,
    val displayTime: String,
    val dateLabel: String,
    val tickStatus: String,
    val voiceDuration: String?,
    val mediaPreviewBase64: String?,
    val localMediaPath: String?,
    val mediaFileName: String?,
    val mediaDuration: String?,
    val reactionEmoji: String?,
    val replyToText: String?,
    val replyToSender: String?,
    val orderIndex: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "local_calls",
    indices = [Index(value = ["userId", "updatedAtMillis"])]
)
data class LocalCallEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val contactName: String,
    val avatarEmoji: String,
    val avatarColorHex: String,
    val avatarImageBase64: String?,
    val callType: String,
    val direction: String,
    val durationText: String,
    val displayTime: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Dao
interface LocalChatDao {
    @Query("SELECT * FROM local_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<LocalProfileEntity?>

    @Query("SELECT * FROM local_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileOnce(userId: String): LocalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LocalProfileEntity)

    @Query("UPDATE local_profiles SET defaultMyName = :defaultMyName, updatedAtMillis = :updatedAtMillis WHERE userId = :userId")
    suspend fun updateDefaultMyName(userId: String, defaultMyName: String, updatedAtMillis: Long)

    @Query("SELECT * FROM local_chats WHERE userId = :userId ORDER BY pinned DESC, updatedAtMillis DESC, createdAtMillis DESC")
    fun observeChats(userId: String): Flow<List<LocalChatEntity>>

    @Query("SELECT * FROM local_chats WHERE userId = :userId AND id = :chatId LIMIT 1")
    fun observeChat(userId: String, chatId: String): Flow<LocalChatEntity?>

    @Query("SELECT * FROM local_chats WHERE userId = :userId ORDER BY pinned DESC, updatedAtMillis DESC, createdAtMillis DESC")
    suspend fun getChatsOnce(userId: String): List<LocalChatEntity>

    @Query("SELECT * FROM local_chats WHERE userId = :userId AND id = :chatId LIMIT 1")
    suspend fun getChatOnce(userId: String, chatId: String): LocalChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: LocalChatEntity)

    @Update
    suspend fun updateChat(chat: LocalChatEntity)

    @Query("DELETE FROM local_chats WHERE userId = :userId AND id = :chatId")
    suspend fun deleteChat(userId: String, chatId: String)

    @Query(
        """
        UPDATE local_chats
        SET lastMessagePreview = :preview,
            lastMessageTime = :displayTime,
            lastMessageSender = :senderRole,
            lastMessageTick = :tickStatus,
            updatedAtMillis = :updatedAtMillis
        WHERE userId = :userId AND id = :chatId
        """
    )
    suspend fun updateChatPreview(
        userId: String,
        chatId: String,
        preview: String,
        displayTime: String,
        senderRole: String,
        tickStatus: String,
        updatedAtMillis: Long
    )

    @Query("SELECT * FROM local_messages WHERE userId = :userId AND chatId = :chatId ORDER BY orderIndex ASC, createdAtMillis ASC")
    fun observeMessages(userId: String, chatId: String): Flow<List<LocalMessageEntity>>

    @Query("SELECT * FROM local_messages WHERE userId = :userId AND chatId = :chatId ORDER BY orderIndex ASC, createdAtMillis ASC")
    suspend fun getMessagesOnce(userId: String, chatId: String): List<LocalMessageEntity>

    @Query("SELECT * FROM local_messages WHERE userId = :userId AND chatId = :chatId AND id = :messageId LIMIT 1")
    suspend fun getMessageOnce(userId: String, chatId: String, messageId: String): LocalMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: LocalMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<LocalMessageEntity>)

    @Query("DELETE FROM local_messages WHERE userId = :userId AND chatId = :chatId AND id = :messageId")
    suspend fun deleteMessage(userId: String, chatId: String, messageId: String)

    @Query("DELETE FROM local_messages WHERE userId = :userId AND chatId = :chatId")
    suspend fun deleteMessagesForChat(userId: String, chatId: String)

    @Transaction
    suspend fun insertChatWithMessages(chat: LocalChatEntity, messages: List<LocalMessageEntity>) {
        upsertChat(chat)
        if (messages.isNotEmpty()) {
            upsertMessages(messages)
        }
    }

    @Transaction
    suspend fun replaceOrAppendChatMessages(
        chat: LocalChatEntity,
        messages: List<LocalMessageEntity>,
        replaceExisting: Boolean
    ) {
        if (replaceExisting) {
            deleteMessagesForChat(chat.userId, chat.id)
        }
        updateChat(chat)
        if (messages.isNotEmpty()) {
            upsertMessages(messages)
        }
    }

    @Transaction
    suspend fun insertMessageAndUpdateChat(
        message: LocalMessageEntity,
        preview: String,
        displayTime: String,
        senderRole: String,
        tickStatus: String,
        updatedAtMillis: Long
    ) {
        upsertMessage(message)
        updateChatPreview(
            userId = message.userId,
            chatId = message.chatId,
            preview = preview,
            displayTime = displayTime,
            senderRole = senderRole,
            tickStatus = tickStatus,
            updatedAtMillis = updatedAtMillis
        )
    }

    @Query("SELECT * FROM local_calls WHERE userId = :userId ORDER BY updatedAtMillis DESC, createdAtMillis DESC")
    fun observeCalls(userId: String): Flow<List<LocalCallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCall(call: LocalCallEntity)

    @Query("DELETE FROM local_calls WHERE userId = :userId AND id = :callId")
    suspend fun deleteCall(userId: String, callId: String)
}

@Database(
    entities = [
        LocalProfileEntity::class,
        LocalChatEntity::class,
        LocalMessageEntity::class,
        LocalCallEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LocalChatDatabase : RoomDatabase() {
    abstract fun chatDao(): LocalChatDao

    companion object {
        @Volatile
        private var INSTANCE: LocalChatDatabase? = null

        fun getInstance(context: Context): LocalChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalChatDatabase::class.java,
                    "mostazachat_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
