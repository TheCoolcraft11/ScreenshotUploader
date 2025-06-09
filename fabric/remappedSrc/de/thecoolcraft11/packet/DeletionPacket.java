package de.thecoolcraft11.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record DeletionPacket(String screenshot) implements CustomPayload {
    public static final Id<DeletionPacket> ID = new Id<>(ModMessages.DELETION_PACKET_ID);
    public static final PacketCodec<RegistryByteBuf, DeletionPacket> CODEC = PacketCodec.tuple(PacketCodecs.STRING, DeletionPacket::screenshot, DeletionPacket::new);


    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
