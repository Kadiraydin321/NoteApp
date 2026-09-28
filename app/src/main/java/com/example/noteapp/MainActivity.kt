package com.example.noteapp

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.noteapp.biometric.BiometricPromptManager
import com.example.noteapp.biometric.BiometricResult
import com.example.noteapp.presentation.detail.NoteDetailScreen
import com.example.noteapp.presentation.detail.NoteDetailViewModel
import com.example.noteapp.presentation.notes.NotesScreen
import com.example.noteapp.presentation.notes.NotesViewModel
import com.example.noteapp.presentation.theme.NoteAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val biometricPromptManager by lazy {
        BiometricPromptManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoteAppTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "notes_screen"
                ) {
                    composable("notes_screen") {
                        val viewModel = hiltViewModel<NotesViewModel>()
                        val state by viewModel.state.collectAsState()

                        NotesScreen(
                            state = state,
                            onSearchChange = viewModel::onSearchQueryChanged,
                            onNoteClick = { note ->
                                if (note.isLocked) {
                                    biometricPromptManager.showBiometricPrompt(
                                        title = "Kilitli Not",
                                        description = "'${note.title}' notunu açmak için parmak izinizi veya PIN'inizi kullanın",
                                        onResult = { result ->
                                            when (result) {
                                                is BiometricResult.AuthenticationSuccess -> {
                                                    navController.navigate("note_detail_screen?noteId=${note.id}")
                                                }
                                                is BiometricResult.AuthenticationError -> {
                                                    Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_SHORT).show()
                                                }
                                                is BiometricResult.AuthenticationFailed -> {
                                                    Toast.makeText(this@MainActivity, "Kimlik doğrulama başarısız", Toast.LENGTH_SHORT).show()
                                                }
                                                else -> {
                                                    Toast.makeText(this@MainActivity, "Biyometrik kilit kullanılamıyor", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    navController.navigate("note_detail_screen?noteId=${note.id}")
                                }
                            },
                            onAddNoteClick = {
                                navController.navigate("note_detail_screen")
                            },
                            onPinNote = viewModel::onPinNote,
                            onArchiveNote = viewModel::onArchiveNote,
                            onMoveToTrash = viewModel::onMoveToTrash,
                            onRestoreNote = viewModel::onRestoreNote,
                            onDeletePermanently = viewModel::onDeleteNotePermanently,
                            onEmptyTrash = viewModel::onEmptyTrash,
                            onCategorySelect = viewModel::onCategorySelect,
                            onViewModeChange = viewModel::setViewMode,
                            onAddCategory = viewModel::onAddCategory
                        )
                    }

                    composable(
                        route = "note_detail_screen?noteId={noteId}",
                        arguments = listOf(
                            navArgument("noteId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            }
                        )
                    ) {
                        val viewModel = hiltViewModel<NoteDetailViewModel>()
                        val state by viewModel.state.collectAsState()

                        NoteDetailScreen(
                            state = state,
                            onTitleChange = viewModel::onTitleChange,
                            onContentValueChange = viewModel::onContentValueChange,
                            onColorChange = viewModel::onColorChange,
                            onTogglePin = viewModel::onTogglePin,
                            onToggleLock = viewModel::onToggleLock,
                            onSetReminder = viewModel::onSetReminder,
                            onAddImageUri = viewModel::onAddImage,
                            onToggleAudioRecording = viewModel::toggleAudioRecording,
                            onToggleAudioPlayback = viewModel::toggleAudioPlayback,
                            onDeleteAttachment = viewModel::onDeleteAttachment,
                            onSaveClick = {
                                viewModel.saveNote()
                                navController.popBackStack()
                            },
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
