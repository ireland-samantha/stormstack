package com.lightningfirefly.engine.rendering.gui;

import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLListView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for multi-selection feature in GLListView.
 */
class GLListViewMultiSelectTest {

    private GLListView listView;

    @BeforeEach
    void setUp() {
        listView = new GLListView(0, 0, 200, 300);
        listView.setItems(List.of("Item A", "Item B", "Item C", "Item D", "Item E"));
    }

    @Test
    void multiSelectEnabled_defaultsToFalse() {
        assertThat(listView.isMultiSelectEnabled()).isFalse();
    }

    @Test
    void setMultiSelectEnabled_enablesMultiSelect() {
        listView.setMultiSelectEnabled(true);
        assertThat(listView.isMultiSelectEnabled()).isTrue();
    }

    @Test
    void singleSelectMode_onlyOneItemSelected() {
        listView.setMultiSelectEnabled(false);

        listView.selectIndex(0);
        listView.selectIndex(2);

        assertThat(listView.getSelectedIndex()).isEqualTo(2);
        assertThat(listView.getSelectedIndices()).containsExactly(2);
    }

    @Test
    void multiSelectMode_multipleItemsCanBeSelected() {
        listView.setMultiSelectEnabled(true);

        listView.selectIndex(0);
        listView.selectIndex(2);
        listView.selectIndex(4);

        assertThat(listView.getSelectedIndices()).containsExactlyInAnyOrder(0, 2, 4);
    }

    @Test
    void multiSelectMode_getSelectedItems_returnsSelectedItems() {
        listView.setMultiSelectEnabled(true);

        listView.selectIndex(1);
        listView.selectIndex(3);

        assertThat(listView.getSelectedItems()).containsExactlyInAnyOrder("Item B", "Item D");
    }

    @Test
    void toggleSelection_addsAndRemovesFromSelection() {
        listView.setMultiSelectEnabled(true);

        listView.toggleSelection(0);
        assertThat(listView.getSelectedIndices()).contains(0);

        listView.toggleSelection(0);
        assertThat(listView.getSelectedIndices()).doesNotContain(0);
    }

    @Test
    void toggleSelection_inSingleSelectMode_togglesBetweenSelectedAndUnselected() {
        listView.setMultiSelectEnabled(false);

        listView.toggleSelection(0);
        assertThat(listView.getSelectedIndex()).isEqualTo(0);

        listView.toggleSelection(0);
        assertThat(listView.getSelectedIndex()).isEqualTo(-1);
    }

    @Test
    void deselectIndex_removesFromSelection() {
        listView.setMultiSelectEnabled(true);
        listView.selectIndex(0);
        listView.selectIndex(1);
        listView.selectIndex(2);

        listView.deselectIndex(1);

        assertThat(listView.getSelectedIndices()).containsExactlyInAnyOrder(0, 2);
    }

    @Test
    void clearSelection_clearsAllSelections() {
        listView.setMultiSelectEnabled(true);
        listView.selectIndex(0);
        listView.selectIndex(2);
        listView.selectIndex(4);

        listView.clearSelection();

        assertThat(listView.getSelectedIndices()).isEmpty();
        assertThat(listView.getSelectedIndex()).isEqualTo(-1);
    }

    @Test
    void selectAll_selectsAllItemsInMultiSelectMode() {
        listView.setMultiSelectEnabled(true);

        listView.selectAll();

        assertThat(listView.getSelectedIndices()).containsExactlyInAnyOrder(0, 1, 2, 3, 4);
    }

    @Test
    void selectAll_selectsFirstItemInSingleSelectMode() {
        listView.setMultiSelectEnabled(false);

        listView.selectAll();

        assertThat(listView.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    void disablingMultiSelect_keepsFirstSelectedIndex() {
        listView.setMultiSelectEnabled(true);
        listView.selectIndex(2);
        listView.selectIndex(4);
        listView.selectIndex(0);

        listView.setMultiSelectEnabled(false);

        // Should keep the smallest index (0)
        assertThat(listView.getSelectedIndex()).isEqualTo(0);
        assertThat(listView.getSelectedIndices()).containsExactly(0);
    }

    @Test
    void enablingMultiSelect_addsCurrentSelectionToSet() {
        listView.setMultiSelectEnabled(false);
        listView.setSelectedIndex(3);

        listView.setMultiSelectEnabled(true);

        assertThat(listView.getSelectedIndices()).contains(3);
    }

    @Test
    void setItems_clearsSelections() {
        listView.setMultiSelectEnabled(true);
        listView.selectIndex(0);
        listView.selectIndex(2);

        listView.setItems(List.of("New Item 1", "New Item 2"));

        assertThat(listView.getSelectedIndices()).isEmpty();
        assertThat(listView.getSelectedIndex()).isEqualTo(-1);
    }

    @Test
    void selectIndex_ignoredForInvalidIndex() {
        listView.setMultiSelectEnabled(true);

        listView.selectIndex(-1);
        listView.selectIndex(100);

        assertThat(listView.getSelectedIndices()).isEmpty();
    }

    @Test
    void getSelectedItems_returnsEmptyListWhenNoSelection() {
        assertThat(listView.getSelectedItems()).isEmpty();
    }

    @Test
    void getSelectedIndices_returnsEmptySetWhenNoSelection() {
        assertThat(listView.getSelectedIndices()).isEmpty();
    }

    @Test
    void getSelectedIndices_returnsCopyNotOriginal() {
        listView.setMultiSelectEnabled(true);
        listView.selectIndex(0);

        Set<Integer> indices = listView.getSelectedIndices();
        indices.clear(); // Modify the copy

        // Original should not be affected
        assertThat(listView.getSelectedIndices()).contains(0);
    }
}
