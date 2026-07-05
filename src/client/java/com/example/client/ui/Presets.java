package com.example.client.ui;

import com.example.NbtEditor;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.util.Unit;

/**
 * Quick-edit presets that mutate the item NBT's {@code components} compound.
 *
 * <p>Rather than hand-writing component NBT (whose shape changes between versions),
 * each preset encodes a real component <em>value</em> through that component's own
 * {@link DataComponentType#codecOrThrow() codec}. The resulting {@link Tag} is exactly
 * what the vanilla item codec produces, and only the targeted component key is touched,
 * so any manual edits elsewhere in the tree are preserved.
 */
final class Presets {
	private Presets() {}

	private static RegistryOps<Tag> ops(RegistryAccess registries) {
		return RegistryOps.create(NbtOps.INSTANCE, registries);
	}

	/** Registered id (e.g. {@code minecraft:custom_name}) used as the components-map key. */
	private static String componentKey(DataComponentType<?> type) {
		return BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).toString();
	}

	/** The {@code components} compound, creating (and linking) it if absent. */
	private static CompoundTag components(CompoundTag root) {
		if (root.get("components") instanceof CompoundTag existing) {
			return existing;
		}
		CompoundTag created = new CompoundTag();
		root.put("components", created);
		return created;
	}

	private static CompoundTag componentsOrNull(CompoundTag root) {
		return root.get("components") instanceof CompoundTag c ? c : null;
	}

	static boolean hasComponent(CompoundTag root, DataComponentType<?> type) {
		CompoundTag c = componentsOrNull(root);
		return c != null && c.contains(componentKey(type));
	}

	static void removeComponent(CompoundTag root, DataComponentType<?> type) {
		CompoundTag c = componentsOrNull(root);
		if (c != null) {
			c.remove(componentKey(type));
		}
	}

	/** Encode {@code value} via its component codec and store it under the components map. */
	static <T> boolean setComponent(CompoundTag root, RegistryAccess registries, DataComponentType<T> type, T value) {
		DataResult<Tag> result = type.codecOrThrow().encodeStart(ops(registries), value);
		Tag encoded = result.result().orElse(null);
		if (encoded == null) {
			NbtEditor.LOGGER.warn("Failed to encode component {}: {}", componentKey(type),
					result.error().map(Object::toString).orElse("unknown"));
			return false;
		}
		components(root).put(componentKey(type), encoded);
		return true;
	}

	// ---- individual presets ----

	/**
	 * Set the {@code minecraft:custom_name} with an explicit base style (from the rename toolbar).
	 * The five format flags are set explicitly (so toggling one off actually clears it), and the
	 * optional colour is applied. Any inline legacy {@code &}/{@code §} codes still layer on top.
	 */
	static void renameStyled(CompoundTag root, RegistryAccess registries, String text,
			boolean bold, boolean italic, boolean underline, boolean strikethrough, boolean obfuscated,
			ChatFormatting color) {
		if (text.isEmpty()) {
			removeComponent(root, DataComponents.CUSTOM_NAME);
			return;
		}
		Style style = Style.EMPTY
				.withBold(bold).withItalic(italic).withUnderlined(underline)
				.withStrikethrough(strikethrough).withObfuscated(obfuscated);
		if (color != null) {
			style = style.withColor(color);
		}
		MutableComponent name = legacyName(text).copy();
		name.setStyle(style); // root base style; inline-code children inherit unset attributes
		setComponent(root, registries, DataComponents.CUSTOM_NAME, name);
	}

	/** The current custom name as plain text (for prefilling the rename box); "" if none. */
	static String currentName(CompoundTag root, RegistryAccess registries) {
		CompoundTag c = componentsOrNull(root);
		if (c == null) {
			return "";
		}
		Tag t = c.get(componentKey(DataComponents.CUSTOM_NAME));
		if (t == null) {
			return "";
		}
		return DataComponents.CUSTOM_NAME.codecOrThrow().parse(ops(registries), t)
				.result().map(Component::getString).orElse("");
	}

	/** The current custom name's root style (for prefilling the rename toolbar); EMPTY if none. */
	static Style currentNameStyle(CompoundTag root, RegistryAccess registries) {
		CompoundTag c = componentsOrNull(root);
		if (c != null) {
			Tag t = c.get(componentKey(DataComponents.CUSTOM_NAME));
			if (t != null) {
				return DataComponents.CUSTOM_NAME.codecOrThrow().parse(ops(registries), t)
						.result().map(Component::getStyle).orElse(Style.EMPTY);
			}
		}
		return Style.EMPTY;
	}

	static void setCount(CompoundTag root, int count) {
		root.putInt("count", Math.max(1, count));
	}

	static int currentCount(CompoundTag root) {
		return root.getIntOr("count", 1);
	}

	static void toggleUnbreakable(CompoundTag root, RegistryAccess registries) {
		if (hasComponent(root, DataComponents.UNBREAKABLE)) {
			removeComponent(root, DataComponents.UNBREAKABLE);
		} else {
			setComponent(root, registries, DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
	}

	/**
	 * Set the given enchantment to {@code level} (or remove it when {@code level <= 0}).
	 * @return false if {@code idStr} is not a known enchantment id.
	 */
	static boolean setEnchantment(CompoundTag root, RegistryAccess registries, String idStr, int level) {
		Identifier id = Identifier.tryParse(idStr.indexOf(':') >= 0 ? idStr : "minecraft:" + idStr);
		if (id == null) {
			return false;
		}
		Optional<Holder.Reference<Enchantment>> holder =
				registries.lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT, id));
		if (holder.isEmpty()) {
			return false;
		}
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(readEnchantments(root, registries));
		if (level <= 0) {
			mutable.removeIf(h -> h.equals(holder.get()));
		} else {
			mutable.set(holder.get(), level);
		}
		ItemEnchantments updated = mutable.toImmutable();
		if (updated.isEmpty()) {
			removeComponent(root, DataComponents.ENCHANTMENTS);
		} else {
			setComponent(root, registries, DataComponents.ENCHANTMENTS, updated);
		}
		return true;
	}

	/** All registered enchantment ids, sorted, for the picker list. */
	static List<Identifier> allEnchantmentIds(RegistryAccess registries) {
		List<Identifier> ids = new ArrayList<>();
		registries.lookupOrThrow(Registries.ENCHANTMENT).listElementIds()
				.forEach(key -> ids.add(key.identifier()));
		ids.sort(Comparator.comparing(Identifier::toString));
		return ids;
	}

	/** Current enchantments on the item as id -> level, for showing levels in the picker. */
	static Map<String, Integer> currentEnchantments(CompoundTag root, RegistryAccess registries) {
		Map<String, Integer> levels = new HashMap<>();
		ItemEnchantments enchantments = readEnchantments(root, registries);
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			holder.unwrapKey().ifPresent(key -> levels.put(key.identifier().toString(), enchantments.getLevel(holder)));
		}
		return levels;
	}

	private static ItemEnchantments readEnchantments(CompoundTag root, RegistryAccess registries) {
		CompoundTag c = componentsOrNull(root);
		if (c != null) {
			Tag t = c.get(componentKey(DataComponents.ENCHANTMENTS));
			if (t != null) {
				return ItemEnchantments.CODEC.parse(ops(registries), t).result().orElse(ItemEnchantments.EMPTY);
			}
		}
		return ItemEnchantments.EMPTY;
	}

	// ---- attribute modifiers ----

	/** All selectable operations, in cycle order. */
	static final List<AttributeModifier.Operation> OPERATIONS = List.of(AttributeModifier.Operation.values());
	/** The equipment-slot groups worth exposing, in cycle order. */
	static final List<EquipmentSlotGroup> SLOT_GROUPS = List.of(
			EquipmentSlotGroup.ANY, EquipmentSlotGroup.MAINHAND, EquipmentSlotGroup.OFFHAND,
			EquipmentSlotGroup.HAND, EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST,
			EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET, EquipmentSlotGroup.ARMOR,
			EquipmentSlotGroup.BODY);

	/** A single modifier's essentials, for prefilling the editor when an attribute is selected. */
	record AttributeState(double amount, AttributeModifier.Operation operation, EquipmentSlotGroup slot) {}

	/** All registered attribute ids, sorted, for the picker list. */
	static List<Identifier> allAttributeIds(RegistryAccess registries) {
		List<Identifier> ids = new ArrayList<>();
		registries.lookupOrThrow(Registries.ATTRIBUTE).listElementIds()
				.forEach(key -> ids.add(key.identifier()));
		ids.sort(Comparator.comparing(Identifier::toString));
		return ids;
	}

	/**
	 * Set (add or replace) this editor's modifier for the given attribute. Existing entries for
	 * the same attribute that were placed by this editor are replaced; other (e.g. vanilla)
	 * entries for the attribute are left untouched.
	 * @return false if {@code idStr} is not a known attribute id.
	 */
	static boolean setAttribute(CompoundTag root, RegistryAccess registries, String idStr, double amount,
			AttributeModifier.Operation operation, EquipmentSlotGroup slot) {
		Holder<Attribute> holder = attributeHolder(registries, idStr);
		if (holder == null) {
			return false;
		}
		Identifier modId = editorModifierId(holder);
		List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(readAttributes(root, registries).modifiers());
		entries.removeIf(e -> e.matches(holder, modId));
		entries.add(new ItemAttributeModifiers.Entry(holder, new AttributeModifier(modId, amount, operation), slot));
		setComponent(root, registries, DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
		return true;
	}

	/** Remove every modifier entry for the given attribute; clears the component if it becomes empty. */
	static boolean removeAttribute(CompoundTag root, RegistryAccess registries, String idStr) {
		String norm = normalizeId(idStr);
		List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(readAttributes(root, registries).modifiers());
		if (!entries.removeIf(e -> attributeId(e.attribute()).equals(norm))) {
			return false;
		}
		if (entries.isEmpty()) {
			removeComponent(root, DataComponents.ATTRIBUTE_MODIFIERS);
		} else {
			setComponent(root, registries, DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
		}
		return true;
	}

	/** The first modifier for the given attribute (amount/operation/slot), for prefilling the editor. */
	static Optional<AttributeState> currentAttribute(CompoundTag root, RegistryAccess registries, String idStr) {
		String norm = normalizeId(idStr);
		for (ItemAttributeModifiers.Entry e : readAttributes(root, registries).modifiers()) {
			if (attributeId(e.attribute()).equals(norm)) {
				AttributeModifier m = e.modifier();
				return Optional.of(new AttributeState(m.amount(), m.operation(), e.slot()));
			}
		}
		return Optional.empty();
	}

	/** Current modifiers as attribute id -> short summary (e.g. {@code "+5 (any)"}), for the picker. */
	static Map<String, String> currentAttributes(CompoundTag root, RegistryAccess registries) {
		Map<String, String> out = new LinkedHashMap<>();
		for (ItemAttributeModifiers.Entry e : readAttributes(root, registries).modifiers()) {
			String id = attributeId(e.attribute());
			if (!id.isEmpty()) {
				out.merge(id, summarize(e.modifier(), e.slot()), (a, b) -> a + ", " + b);
			}
		}
		return out;
	}

	static String operationLabel(AttributeModifier.Operation op) {
		return switch (op) {
			case ADD_VALUE -> "+val";
			case ADD_MULTIPLIED_BASE -> "×base";
			case ADD_MULTIPLIED_TOTAL -> "×total";
		};
	}

	static String slotLabel(EquipmentSlotGroup slot) {
		return slot.getSerializedName();
	}

	private static String summarize(AttributeModifier modifier, EquipmentSlotGroup slot) {
		String op = modifier.operation() == AttributeModifier.Operation.ADD_VALUE ? "" : " " + operationLabel(modifier.operation());
		return formatAmount(modifier.amount()) + op + " (" + slot.getSerializedName() + ")";
	}

	static String formatAmount(double amount) {
		String s = amount == Math.rint(amount) && !Double.isInfinite(amount)
				? Long.toString((long) amount)
				: Double.toString(amount);
		return (amount > 0 ? "+" : "") + s;
	}

	private static ItemAttributeModifiers readAttributes(CompoundTag root, RegistryAccess registries) {
		CompoundTag c = componentsOrNull(root);
		if (c != null) {
			Tag t = c.get(componentKey(DataComponents.ATTRIBUTE_MODIFIERS));
			if (t != null) {
				return ItemAttributeModifiers.CODEC.parse(ops(registries), t).result().orElse(ItemAttributeModifiers.EMPTY);
			}
		}
		return ItemAttributeModifiers.EMPTY;
	}

	private static Holder<Attribute> attributeHolder(RegistryAccess registries, String idStr) {
		Identifier id = Identifier.tryParse(normalizeId(idStr));
		if (id == null) {
			return null;
		}
		return registries.lookupOrThrow(Registries.ATTRIBUTE)
				.get(ResourceKey.create(Registries.ATTRIBUTE, id))
				.map(h -> (Holder<Attribute>) h)
				.orElse(null);
	}

	/** Stable per-attribute modifier id so re-editing the same attribute replaces in place. */
	private static Identifier editorModifierId(Holder<Attribute> holder) {
		return Identifier.fromNamespaceAndPath("nbt_editor", attributeId(holder).replace(':', '.'));
	}

	private static String attributeId(Holder<Attribute> holder) {
		return holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
	}

	private static String normalizeId(String idStr) {
		return idStr.indexOf(':') >= 0 ? idStr : "minecraft:" + idStr;
	}

	// ---- text helpers ----

	/**
	 * Build a text component from a string, honouring legacy {@code &}/{@code §} colour and
	 * format codes. A colour code resets active formats, matching vanilla legacy behaviour.
	 */
	static Component legacyName(String s) {
		if (s.indexOf('&') < 0 && s.indexOf('§') < 0) {
			return Component.literal(s);
		}
		MutableComponent out = Component.empty();
		Style style = Style.EMPTY;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < s.length()) {
				char code = Character.toLowerCase(s.charAt(i + 1));
				ChatFormatting fmt = ChatFormatting.getByCode(code);
				if (fmt != null) {
					if (run.length() > 0) {
						out.append(Component.literal(run.toString()).withStyle(style));
						run.setLength(0);
					}
					if (code == 'r') {
						style = Style.EMPTY;
					} else if (fmt.isColor()) {
						style = Style.EMPTY.withColor(fmt);
					} else {
						style = style.applyFormat(fmt);
					}
					i++;
					continue;
				}
			}
			run.append(c);
		}
		if (run.length() > 0) {
			out.append(Component.literal(run.toString()).withStyle(style));
		}
		return out;
	}
}
