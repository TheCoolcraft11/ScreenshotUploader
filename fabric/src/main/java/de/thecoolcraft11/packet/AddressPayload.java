package de.thecoolcraft11.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record AddressPayload(String message) implements CustomPayload {
    public static final CustomPayload.Id<AddressPayload> ID = new CustomPayload.Id<>(ModMessages.ADDRESS_PACKET_ID);
    public static final PacketCodec<RegistryByteBuf, AddressPayload> CODEC = PacketCodec.tuple(PacketCodecs.STRING, AddressPayload::message, AddressPayload::new);


    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
