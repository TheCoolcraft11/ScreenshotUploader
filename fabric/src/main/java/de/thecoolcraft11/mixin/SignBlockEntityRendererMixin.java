package de.thecoolcraft11.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import de.thecoolcraft11.config.ConfigManager;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(value = AbstractSignBlockEntityRenderer.class, priority = 100000)
public class SignBlockEntityRendererMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("ScreenshotUploader");

    @Unique
    private static final Map<String, Identifier> SCREENSHOT_TEXTURES = new HashMap<>();

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
            if (entity.getWorld() != null) {

                matrices.push();

                String text = entity.getFrontText().getMessage(0, false).getString() +
                        entity.getFrontText().getMessage(1, false).getString() +
                        entity.getFrontText().getMessage(2, false).getString() +
                        entity.getFrontText().getMessage(3, false).getString() +
                        entity.getBackText().getMessage(0, false).getString() +
                        entity.getBackText().getMessage(1, false).getString() +
                        entity.getBackText().getMessage(2, false).getString() +
                        entity.getBackText().getMessage(3, false).getString();

                String urlPattern = "(https?://[\\w.-]+(?::\\d+)?(?:/[\\w.-]*)*)(\\[-?\\d+(?:[.,]\\d+)?(?:[;,:]-?\\w+(?:[.:,]\\d+)?)*?])?";
                Pattern pattern = Pattern.compile(urlPattern);
                Matcher matcher = pattern.matcher(text);
                try {
                    String highlightItemName = ConfigManager.getClientConfig().highlightItem;
                    float customX = 0;
                    float customY = 0;
                    float customZ = 0;
                    float customYaw = 0;
                    float customPitch = 0;
                    float customSize = 0.75f;
                    int customLight = Math.min(light + ConfigManager.getClientConfig().imageLightBoost, 15728880);
                    boolean useCustomTransformations = false;

                    if (matcher.find() && entity.isWaxed()) {
                        String url = matcher.group(1);
                        String transformations = matcher.group(2);
                        boolean isWall = isWallSign(entity.getWorld(), entity.getPos());
                        boolean isHanging = isHangingSign(entity.getWorld(), entity.getPos());
                        boolean isWallHanging = isWallHangingSign(entity.getWorld(), entity.getPos());
                        Direction facing = Direction.SOUTH;

                        if (transformations != null && !transformations.isEmpty()) {
                            try {
                                String cleanTransformations = transformations.substring(1, transformations.length() - 1);
                                String[] parts = cleanTransformations.split("[;,]");
                                if (parts.length >= 1) {
                                    customX = Float.parseFloat(parts[0]);
                                    if (parts.length >= 2) {
                                        customY = -Float.parseFloat(parts[1]);
                                        if (parts.length >= 3) {
                                            customZ = Float.parseFloat(parts[2]);
                                            if (parts.length >= 4) {
                                                customYaw = Float.parseFloat(parts[3]);
                                                if (parts.length >= 5) {
                                                    customPitch = Float.parseFloat(parts[4]);
                                                    if (parts.length >= 6) {
                                                        customSize = Float.parseFloat(parts[5]);
                                                        if (parts.length >= 7) {
                                                            customLight = Math.min(Integer.parseInt(parts[6]), 15728880);
                                                            if (parts.length >= 8) {
                                                                highlightItemName = parts[7];
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    useCustomTransformations = true;
                                }
                            } catch (Exception e) {
                                LOGGER.error("Failed to parse transformation string: {}", transformations, e);
                            }
                        }

                        if (isWall || isWallHanging) {
                            facing = entity.getWorld().getBlockState(entity.getPos()).get(Properties.HORIZONTAL_FACING);
                        }

                        Identifier signTexture;
                        if (isHanging || isWallHanging) {
                            signTexture = Identifier.of("minecraft", "textures/entity/signs/hanging/" + woodType.name().toLowerCase() + ".png");
                            if (ConfigManager.getClientConfig().useCustomSign && !useCustomTransformations)
                                signTexture = Identifier.of("screenshot-uploader", "textures/entity/signs/hanging_screenshot.png");
                        } else {
                            signTexture = Identifier.of("minecraft", "textures/entity/signs/" + woodType.name().toLowerCase() + ".png");
                            if (ConfigManager.getClientConfig().useCustomSign && !useCustomTransformations)
                                signTexture = Identifier.of("screenshot-uploader", "textures/entity/signs/screenshot.png");
                        }

                        float scale = 0.75f;
                        if (!useCustomTransformations && ConfigManager.getClientConfig().highlightOscillation) {
                            long elapsedTime = System.nanoTime();
                            float oscillation = (float) Math.sin(elapsedTime / 5000000000.0 * Math.PI * 2);

                            scale = 0.05f * oscillation + 0.80f;
                        }

                        float alpha = ConfigManager.getClientConfig().highlightColorA;
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

                        matrices.push();

                        if (isWallHanging) {
                            matrices.translate(0.5f, 0.62223f, 0.92222f);

                            float yRotation = 0;
                            float offset = 0.42f;

                            switch (facing) {
                                case NORTH:
                                    yRotation = 180f;
                                    matrices.translate(0, 0, offset);
                                    break;
                                case SOUTH:
                                    matrices.translate(0, 0, -offset);
                                    break;
                                case EAST:
                                    yRotation = 270f;
                                    matrices.translate(-offset, 0, 0);
                                    break;
                                case WEST:
                                    yRotation = 90f;
                                    matrices.translate(offset, 0, 0);
                                    break;
                            }

                            if (yRotation != 0) {
                                matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(yRotation)));
                            }

                        } else if (isWall) {
                            matrices.translate(0.5f, 0.123f, 0.5f);

                            float yRotation = 0;
                            float zOffset = 0.434f;

                            switch (facing) {
                                case NORTH:
                                    yRotation = 180f;
                                    matrices.translate(0, 0, zOffset);
                                    break;
                                case SOUTH:
                                    matrices.translate(0, 0, -zOffset);
                                    break;
                                case EAST:
                                    yRotation = 270f;
                                    matrices.translate(-zOffset, 0, 0);
                                    break;
                                case WEST:
                                    yRotation = 90f;
                                    matrices.translate(zOffset, 0, 0);
                                    break;
                            }

                            if (yRotation != 0) {
                                matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(yRotation)));
                            }
                        } else {
                            matrices.translate(0.5f, 0.45f, 0.5f);

                            try {
                                float rotation = entity.getWorld().getBlockState(entity.getPos()).get(Properties.ROTATION) * 22.5f;
                                matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(-rotation)));
                            } catch (Exception ignored) {
                            }
                        }

                        if ((isHanging || isWallHanging)) {
                            scale *= 1.478865f;
                        }

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

                        Identifier screenshotTexture = getScreenshotTexture(url);
                        if (screenshotTexture != null && ConfigManager.getClientConfig().enableScreenshotRendering) {
                            renderScreenshotOnSign(matrices, vertexConsumers, screenshotTexture, isWallHanging || isHanging, useCustomTransformations, customX, customY, customZ, customYaw, customPitch, customSize, customLight);
                        }

                        matrices.pop();
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    }

                    if (matcher.matches() && entity.isWaxed()) {
                        matrices.push();

                        boolean isWall = isWallSign(entity.getWorld(), entity.getPos());
                        boolean isHanging = isHangingSign(entity.getWorld(), entity.getPos());
                        boolean isWallHanging = isWallHangingSign(entity.getWorld(), entity.getPos());
                        Direction facing = Direction.SOUTH;

                        if (isWall || isWallHanging) {
                            facing = entity.getWorld().getBlockState(entity.getPos()).get(Properties.HORIZONTAL_FACING);
                        }

                        if (isWallHanging) {
                            matrices.translate(0.5, 1.5f, 0.5);

                            float offset = 0.15f;
                            switch (facing) {
                                case NORTH:
                                    matrices.translate(0, 0, offset);
                                    break;
                                case SOUTH:
                                    matrices.translate(0, 0, -offset);
                                    break;
                                case EAST:
                                    matrices.translate(-offset, 0, 0);
                                    break;
                                case WEST:
                                    matrices.translate(offset, 0, 0);
                                    break;
                            }
                        } else if (isHanging) {
                            matrices.translate(0.5, 1.9f, 0.5);
                        } else if (isWall) {
                            matrices.translate(0.5, 1.25f, 0.5);

                            float offset = 0.2f;
                            switch (facing) {
                                case NORTH:
                                    matrices.translate(0, 0, offset);
                                    break;
                                case SOUTH:
                                    matrices.translate(0, 0, -offset);
                                    break;
                                case EAST:
                                    matrices.translate(-offset, 0, 0);
                                    break;
                                case WEST:
                                    matrices.translate(offset, 0, 0);
                                    break;
                            }
                        } else {
                            matrices.translate(0.5, 1.66f, 0.5);
                        }

                        float horizontalOffset = ConfigManager.getClientConfig().itemHorizontalOffset;
                        float verticalOffset = ConfigManager.getClientConfig().itemVerticalOffset;
                        float depthOffset = ConfigManager.getClientConfig().itemDepthOffset;
                        float itemScale = ConfigManager.getClientConfig().itemScale;

                        if (isWall || isWallHanging) {
                            switch (facing) {
                                case NORTH:
                                    matrices.translate(horizontalOffset, verticalOffset, -depthOffset);
                                    break;
                                case SOUTH:
                                    matrices.translate(-horizontalOffset, verticalOffset, depthOffset);
                                    break;
                                case EAST:
                                    matrices.translate(depthOffset, verticalOffset, horizontalOffset);
                                    break;
                                case WEST:
                                    matrices.translate(-depthOffset, verticalOffset, -horizontalOffset);
                                    break;
                            }
                        } else {
                            matrices.translate(horizontalOffset, verticalOffset, depthOffset);
                        }

                        matrices.scale(itemScale, itemScale, itemScale);

                        rotationAngle += ConfigManager.getClientConfig().highlightRotationSpeed;
                        if (rotationAngle >= 360.0f) {
                            rotationAngle = 0.0f;
                        }

                        if (ConfigManager.getClientConfig().rotateHighlightItem) {
                            float radians = rotationAngle * (float) Math.PI / 180.0f;
                            Quaternionf rotation = new Quaternionf(0.0f, MathHelper.sin(radians / 2), 0.0f, MathHelper.cos(radians / 2));
                            matrices.multiply(rotation);
                        }

                        Identifier itemIdentifier = Identifier.tryParse(highlightItemName);
                        ItemStack itemStack = Registries.ITEM.get(itemIdentifier).getDefaultStack();

                        itemRenderer.renderItem(itemStack, ItemDisplayContext.FIXED, customLight, overlay, matrices, vertexConsumers, entity.getWorld(), 0);

                        matrices.pop();
                    }
                } finally {
                    matrices.pop();
                    if (ConfigManager.getClientConfig().hideSign && matcher.matches() && entity.isWaxed()) ci.cancel();
                }
            }
        }
    }

    @Unique
    private void renderScreenshotOnSign(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        Identifier textureId, boolean isHanging, boolean useCustomTransformations,
                                        float customX, float customY, float customZ, float customYaw, float customPitch, float customSize, int customLight) {
        matrices.push();

        if (isHanging) {
            matrices.translate(0, ConfigManager.getClientConfig().hangingSignVerticalOffset, 0);
        } else {
            matrices.translate(0, ConfigManager.getClientConfig().regularSignVerticalOffset, 0);
        }

        matrices.translate(0, 0, ConfigManager.getClientConfig().imageZOffset);

        if (useCustomTransformations) {
            matrices.translate(0, 0, 0);

            matrices.translate(customX, customY, customZ);

            matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(-customYaw)));
            matrices.multiply(new Quaternionf().rotationX((float) Math.toRadians(customPitch)));
        }

        float width = isHanging ?
                ConfigManager.getClientConfig().hangingSignImageWidth :
                ConfigManager.getClientConfig().regularSignImageWidth;
        float height = isHanging ?
                ConfigManager.getClientConfig().hangingSignImageHeight :
                ConfigManager.getClientConfig().regularSignImageHeight;

        if (useCustomTransformations) {
            float sizeFactor = customSize / 0.75f;
            width *= sizeFactor;
            height *= sizeFactor;
        }

        if (ConfigManager.getClientConfig().preserveImageAspectRatio) {
            NativeImageBackedTexture texture = getTextureFromId(textureId);
            if (texture != null && texture.getImage() != null) {
                int imageWidth = texture.getImage().getWidth();
                int imageHeight = texture.getImage().getHeight();

                if (imageWidth > 0 && imageHeight > 0) {
                    float imageRatio = (float) imageWidth / imageHeight;
                    float targetRatio = width / height;

                    if (imageRatio > targetRatio) {
                        height = width / imageRatio;
                    } else {
                        width = height * imageRatio;
                    }
                }
            }
        }

        matrices.translate(-width / 2, -height / 2, 0);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderLayer renderLayer = RenderLayer.getEntityTranslucent(textureId);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);

        int r = ConfigManager.getClientConfig().imageColorR;
        int g = ConfigManager.getClientConfig().imageColorG;
        int b = ConfigManager.getClientConfig().imageColorB;
        int a = ConfigManager.getClientConfig().imageColorA;

        if (r == 0 && g == 0 && b == 0) {
            r = g = b = 255;
        }


        vertexConsumer.vertex(matrix, 0, 0, 0).color(r, g, b, a).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(customLight).normal(0, 0, 1);
        vertexConsumer.vertex(matrix, 0, height, 0).color(r, g, b, a).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(customLight).normal(0, 0, 1);
        vertexConsumer.vertex(matrix, width, height, 0).color(r, g, b, a).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(customLight).normal(0, 0, 1);
        vertexConsumer.vertex(matrix, width, 0, 0).color(r, g, b, a).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(customLight).normal(0, 0, 1);

        matrices.pop();
    }

    @Unique
    private NativeImageBackedTexture getTextureFromId(Identifier id) {
        try {
            return (NativeImageBackedTexture) MinecraftClient.getInstance().getTextureManager().getTexture(id);
        } catch (Exception e) {
            LOGGER.warn("Failed to get texture from id: {}", id);
            return null;
        }
    }

    @Unique
    private Identifier getScreenshotTexture(String url) {
        if (SCREENSHOT_TEXTURES.containsKey(url)) {
            return SCREENSHOT_TEXTURES.get(url);
        }

        try {
            String cacheFileName = "screenshots_cache/" + url.hashCode() + ".png";
            File cachedImage = new File(cacheFileName);

            if (cachedImage.exists()) {
                try (InputStream fileInputStream = Files.newInputStream(cachedImage.toPath());
                     NativeImage loadedImage = NativeImage.read(fileInputStream)) {
                    Identifier textureId = Identifier.of("screenshot-uploader", "signs/" + url.hashCode());
                    MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(String::new, loadedImage));
                    SCREENSHOT_TEXTURES.put(url, textureId);
                    return textureId;
                } catch (IOException e) {
                    LOGGER.error("Failed to load cached screenshot image: {}", e.getMessage());
                }
            }


        } catch (Exception e) {
            LOGGER.error("Error processing screenshot texture: {}", e.getMessage());
        }

        return null;
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

    @Unique
    public boolean isWallHangingSign(World world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.getBlock() instanceof WallHangingSignBlock;
    }
}
