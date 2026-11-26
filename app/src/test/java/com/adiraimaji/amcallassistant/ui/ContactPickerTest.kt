package com.adiraimaji.amcallassistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPickerTest {
    private val choices = listOf(
        ContactChoice("Lena", "123", "content://contacts/1"),
        ContactChoice("Lena", "456", "content://contacts/2"),
        ContactChoice("Sam", "789", "content://contacts/3"),
    )

    @Test fun allContactsShowWithoutAQuery() {
        assertEquals(choices, matchingContacts(choices, ""))
    }

    @Test fun aNameMatchesEveryNumberOfThatContact() {
        assertEquals(listOf(choices[0], choices[1]), matchingContacts(choices, "lena"))
    }

    @Test fun aNumberFindsItsOwnRow() {
        assertEquals(listOf(choices[1]), matchingContacts(choices, "45"))
    }

    @Test fun noMatchIsEmpty() {
        assertEquals(emptyList<ContactChoice>(), matchingContacts(choices, "nobody"))
    }
}
