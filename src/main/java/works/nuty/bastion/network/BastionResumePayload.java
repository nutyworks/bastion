package works.nuty.bastion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record BastionResumePayload() implements CustomPacketPayload {
    public static final Type<BastionResumePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bastion", "resume"));
    public static final StreamCodec<FriendlyByteBuf, BastionResumePayload> CODEC = StreamCodec.unit(new BastionResumePayload());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}