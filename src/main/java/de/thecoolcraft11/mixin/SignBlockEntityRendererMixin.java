package de.thecoolcraft11.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import de.thecoolcraft11.config.ConfigManager;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(SignBlockEntityRenderer.class)
public class SignBlockEntityRendererMixin {

    @Unique
    ItemRenderer itemRenderer;

    @Inject(method = "<init>(Lnet/minecraft/client/render/block/entity/BlockEntityRendererFactory$Context;)V",
            at = @At("RETURN"))
    private void onConstructor(BlockEntityRendererFactory.Context context, CallbackInfo ci) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Unique
    private float rotationAngle = 0f;

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/block/BlockState;Lnet/minecraft/block/AbstractSignBlock;Lnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model;)V",
            at = @At("HEAD"), cancellable = true)
    void render(SignBlockEntity entity,
                MatrixStack matrices,
                VertexConsumerProvider vertexConsumers,
                int light,
                int overlay,
                BlockState state,
                AbstractSignBlock block,
                WoodType woodType,
                Model model,
                CallbackInfo ci) {
        if (ConfigManager.getClientConfig().highlightScreenshotSigns) {
            if (entity.getWorld() != null && !isHangingSign(entity.getWorld(), entity.getPos())) {

                matrices.push();
                try {
                    String text = entity.getFrontText().getMessage(0, false).getString() +
                            entity.getFrontText().getMessage(1, false).getString() +
                            entity.getFrontText().getMessage(2, false).getString() +
                            entity.getFrontText().getMessage(3, false).getString() +
                            entity.getBackText().getMessage(0, false).getString() +
                            entity.getBackText().getMessage(1, false).getString() +
                            entity.getBackText().getMessage(2, false).getString() +
                            entity.getBackText().getMessage(3, false).getString();

                    String urlPattern = "https?://[\\w.-]+(:\\d+)?(/[\\w.-]*)*";
                    Pattern pattern = Pattern.compile(urlPattern);
                    Matcher matcher = pattern.matcher(text);

                    if (matcher.matches() && entity.isWaxed()) {
                        Identifier signTexture = Identifier.of("minecraft", "textures/entity/signs/" + woodType.name().toLowerCase() + ".png");
                        RenderSystem.setShaderTexture(0, signTexture);

                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();

                        float scale = 0.75f;
                        if (ConfigManager.getClientConfig().highlightOscillation) {
                            long elapsedTime = System.nanoTime();
                            float oscillation = (float) Math.sin(elapsedTime / 5000000000.0 * Math.PI * 2);


                            scale = 0.05f * oscillation + 0.80f;
                        }

                        float alpha = ConfigManager.getClientConfig().highlightColorA;
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

                        matrices.push();
                        matrices.translate(0.5f, isWallSign(entity.getWorld(), entity.getPos()) ? 0.123f : 0.45f, isWallSign(entity.getWorld(), entity.getPos()) ? 0.066f : 0.5f);
                        matrices.scale(scale, -scale, scale);

                        if (ConfigManager.getClientConfig().rotateHighlightSign) {
                            float radians = rotationAngle * (float) Math.PI / 180.0f;
                            Quaternionf rotation = new Quaternionf(0.0f, MathHelper.sin(radians / 2), 0.0f, MathHelper.cos(radians / 2));
                            matrices.multiply(rotation);
                        }

                        VertexConsumer outlineConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(signTexture));
                        int alphaValue = (int) (alpha * 255);
                        int color = (alphaValue << 24) | (ConfigManager.getClientConfig().highlightColorR << 16) | (ConfigManager.getClientConfig().highlightColorG << 8) | (ConfigManager.getClientConfig().highlightColorB);

                        model.render(matrices, outlineConsumer, light, overlay, color);

                        matrices.pop();
                        RenderSystem.disableBlend();
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    }

                    if (matcher.matches() && entity.isWaxed()) {
                        matrices.push();

                        matrices.translate(0.5, entity.getWorld() != null ? isWallSign(entity.getWorld(), entity.getPos()) ? 1.25f : 1.66f : 1.5f, 0.5);
                        matrices.scale(0.66f, 0.66f, 0.66f);

                        rotationAngle += ConfigManager.getClientConfig().highlightRotationSpeed;
                        if (rotationAngle >= 360.0f) {
                            rotationAngle = 0.0f;
                        }


                        if (ConfigManager.getClientConfig().rotateHighlightItem) {
                            float radians = rotationAngle * (float) Math.PI / 180.0f;
                            Quaternionf rotation = new Quaternionf(0.0f, MathHelper.sin(radians / 2), 0.0f, MathHelper.cos(radians / 2));
                            matrices.multiply(rotation);
                        }

                        Identifier itemIdentifier = Identifier.tryParse(ConfigManager.getClientConfig().highlightItem);
                        ItemStack itemStack = Registries.ITEM.get(itemIdentifier).getDefaultStack();

                        itemRenderer.renderItem(itemStack, ModelTransformationMode.FIXED, light, overlay, matrices, vertexConsumers, entity.getWorld(), 0);

                        matrices.pop();
                    }
                } finally {
                    matrices.pop();
                    if (ConfigManager.getClientConfig().hideSign) ci.cancel();
                }
            }
        }
    }


    @Unique
    public boolean isWallSign(World world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.getBlock() instanceof WallSignBlock;
    }

    @Unique
    public boolean isHangingSign(World world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.getBlock() instanceof HangingSignBlock || blockState.getBlock() instanceof WallHangingSignBlock;
    }
}
