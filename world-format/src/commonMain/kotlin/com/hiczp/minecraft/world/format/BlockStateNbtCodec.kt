package com.hiczp.minecraft.world.format

import com.hiczp.minecraft.nbt.NbtCompound
import com.hiczp.minecraft.nbt.NbtString
import com.hiczp.minecraft.nbt.NbtTag

/**
 * The saved shape of one block state: the field names of its identifier and property compound, and whether a bare
 * identifier string is accepted as the compact form.
 *
 * [ID_PROPERTIES] is the selected-release form. [NAME_PROPERTIES] is the capitalized `Name`/`Properties` compound
 * written by earlier releases; it has no compact form. A layout only renames fields. It does not migrate block IDs,
 * property values or any other part of an earlier save, so callers must still supply definitions matching that save.
 */
enum class BlockStateNbtLayout(val idField: String, val propertiesField: String, val compactIdentifier: Boolean) {
    ID_PROPERTIES("id", "properties", true),
    NAME_PROPERTIES("Name", "Properties", false),
}

/**
 * Decodes block states saved in [layout]: compact identifiers where the layout allows them, and full compounds.
 * [defaultProperties] supplies the block's complete default state when the saved value omits some or all properties.
 * Bind a release- or world-scoped definition provider; the decoder never substitutes vanilla data implicitly.
 * Explicit properties remain open canonical strings, allowing caller-defined blocks and state properties.
 */
class BlockStateNbtDecoder(
    val layout: BlockStateNbtLayout = BlockStateNbtLayout.ID_PROPERTIES,
    val defaultProperties: (BlockId) -> StateProperties,
) {
    fun decode(nbtTag: NbtTag): BlockState {
        if (nbtTag is NbtString && !layout.compactIdentifier) {
            throw NbtPropertyFormatException("Block state layout $layout has no compact identifier form")
        }
        val nbtCompound = if (nbtTag is NbtString) null else nbtTag.compound()
        val blockId = BlockId.parse(nbtCompound?.string(layout.idField) ?: nbtTag.string())
        val defaults = defaultProperties(blockId)
        val encodedProperties = nbtCompound?.optionalTag<NbtCompound>(layout.propertiesField)
        val properties = if (encodedProperties == null || encodedProperties.size == 0) defaults else StateProperties(
            buildMap {
                putAll(defaults.asMap())
                encodedProperties.forEachEntry { name, tag -> put(name, tag.string()) }
            },
        )
        return BlockState(blockId, properties)
    }
}

/** Encodes the full block-state compound in [layout], preserving all properties without requiring default-state data. */
class BlockStateNbtEncoder(val layout: BlockStateNbtLayout = BlockStateNbtLayout.ID_PROPERTIES) {
    fun encode(blockState: BlockState): NbtCompound = NbtCompound(buildMap {
        put(layout.idField, NbtString(blockState.blockId.toString()))
        if (blockState.properties.size != 0) {
            put(
                layout.propertiesField,
                NbtCompound(blockState.properties.associate { (name, value) -> name to NbtString(value) })
            )
        }
    })
}
