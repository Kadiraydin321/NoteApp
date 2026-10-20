package com.example.noteapp

import com.example.noteapp.domain.util.ChecklistSorter
import org.junit.Assert.*
import org.junit.Test

class ChecklistSorterTest {

    @Test
    fun moveCheckedToBottom_reordersCompletedItemsToEndOfBlock() {
        val input = """
            # Görev Listesi
            - [x] Kahvaltı yap
            - [ ] Kod yaz
            - [x] E-postaları kontrol et
            - [ ] Testleri çalıştır
            
            Bu bir ara paragraftır.
            
            - [x] İkinci blok biten
            - [ ] İkinci blok bekleyen
        """.trimIndent()

        val expected = """
            # Görev Listesi
            - [ ] Kod yaz
            - [ ] Testleri çalıştır
            - [x] Kahvaltı yap
            - [x] E-postaları kontrol et
            
            Bu bir ara paragraftır.
            
            - [ ] İkinci blok bekleyen
            - [x] İkinci blok biten
        """.trimIndent()

        val result = ChecklistSorter.moveCheckedToBottom(input)
        assertEquals(expected, result)
    }

    @Test
    fun moveCheckedToBottom_returnsOriginalIfNoChecklists() {
        val input = "Sadece düz bir paragraf.\nİkinci satır."
        assertEquals(input, ChecklistSorter.moveCheckedToBottom(input))
    }

    @Test
    fun toggleAllItems_checkAllAndUncheckAllWorkProperly() {
        val input = """
            - [ ] Görev 1
              - [ ] Alt görev 2
            - [x] Görev 3
        """.trimIndent()

        // 1. Tümünü Tamamla
        val allChecked = ChecklistSorter.toggleAllItems(input, checkAll = true)
        val expectedAllChecked = """
            - [x] Görev 1
              - [x] Alt görev 2
            - [x] Görev 3
        """.trimIndent()
        assertEquals(expectedAllChecked, allChecked)

        // 2. Tüm İşaretleri Kaldır
        val allUnchecked = ChecklistSorter.toggleAllItems(allChecked, checkAll = false)
        val expectedAllUnchecked = """
            - [ ] Görev 1
              - [ ] Alt görev 2
            - [ ] Görev 3
        """.trimIndent()
        assertEquals(expectedAllUnchecked, allUnchecked)
    }

    @Test
    fun removeCompletedItems_filtersOnlyCheckedLines() {
        val input = """
            # Başlık
            - [x] Biten görev
            - [ ] Devam eden görev
            Normal not içeriği.
            - [x] Başka bir biten görev
        """.trimIndent()

        val expected = """
            # Başlık
            - [ ] Devam eden görev
            Normal not içeriği.
        """.trimIndent()

        val result = ChecklistSorter.removeCompletedItems(input)
        assertEquals(expected, result)
    }
}
