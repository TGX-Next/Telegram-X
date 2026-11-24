/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
// region @MadeByLLM("gpt-6", id = "android13-arm64")
// @MadeByLLM("gpt-6", id = "camerax-only")
package org.thunderdog.challegram.ui.camera.x;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.common.util.concurrent.ListenableFuture;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.ui.camera.CameraDelegate;
import org.thunderdog.challegram.ui.camera.CameraFeatures;
import org.thunderdog.challegram.ui.camera.CameraManager;
import org.thunderdog.challegram.ui.camera.CameraQrBridge;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;


// @MadeByLLM("gpt-6", id = "camerax-lifecycle-fixes")
public class CameraManagerX extends CameraManager<PreviewView> {
  public CameraManagerX (Context context, CameraDelegate delegate) {
    super(context, delegate);
  }

  @Override
  protected PreviewView onCreateView () {
    PreviewView view = new PreviewView(context);
    view.addOnLayoutChangeListener(
      (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        prepareBitmaps(v.getMeasuredWidth(), v.getMeasuredHeight());
        if (preview == null) {
          bindPreview();
        }
      }
    );
    view.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    previewStreamObserver = state -> {
      if (state == PreviewView.StreamState.STREAMING && waitingForPreview && isOpen && !isPaused) {
        waitingForPreview = false;
        delegate.onRenderedFirstFrame();
      }
    };
    view.getPreviewStreamState().observe((LifecycleOwner) context, previewStreamObserver);
    return view;
  }

  private boolean isOpen;
  private ProcessCameraProvider cameraProvider;
  private ImageCapture imageCapture;
  private VideoCapture<Recorder> videoCapture;
  private Recording videoRecording;
  private Preview preview;
  private boolean waitingForPreview;
  private Observer<PreviewView.StreamState> previewStreamObserver;
  private boolean videoCapturePending;
  private int openGeneration;
  private ImageAnalysis imageAnalyzer;
  private Camera camera;
  private LiveData<CameraState> lastCameraState;
  private int flashMode = ImageCapture.FLASH_MODE_OFF;
  private boolean originalFacing;
  private CameraQrBridge cameraQrBridge;

  @Override
  public void openCamera () {
    if (isOpen) {
      return;
    }
    this.isOpen = true;
    final int generation = ++openGeneration;
    this.originalFacing = preferFrontFacingCamera();
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
      ProcessCameraProvider.getInstance(context);
    cameraProviderFuture.addListener(() -> {
      try {
        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
        if (isOpen && generation == openGeneration) {
          this.cameraProvider = cameraProvider;
          this.bindPreview();
        }
      } catch (ExecutionException e) {
        Log.e(Log.TAG_CAMERA, "Unable to initialize CameraX", e);
        if (isOpen && generation == openGeneration) {
          delegate.displayFatalErrorMessage(e.getMessage());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        Log.e(Log.TAG_CAMERA, "CameraX initialization interrupted", e);
      }
    }, ContextCompat.getMainExecutor(context));
  }

  private void bindPreview () {
    if (!isOpen || isPaused || cameraProvider == null ||
      cameraView.getWidth() == 0 || cameraView.getHeight() == 0) {
      return;
    }

    unbindCamera();
    waitingForPreview = true;

    AspectRatioStrategy aspectRatioStrategy;
    int videoAspectRatio;
    int aspectRatioMode = Settings.instance().getCameraAspectRatioMode();
    switch (aspectRatioMode) {
      case Settings.CAMERA_RATIO_FULL_SCREEN:
      case Settings.CAMERA_RATIO_16_9:
        aspectRatioStrategy = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY;
        videoAspectRatio = AspectRatio.RATIO_16_9;
        break;
      case Settings.CAMERA_RATIO_1_1:
      case Settings.CAMERA_RATIO_4_3:
      default:
        aspectRatioStrategy = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY;
        videoAspectRatio = AspectRatio.RATIO_4_3;
        break;
    }

    ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
      .setAspectRatioStrategy(aspectRatioStrategy)
      .build();

    final int lensFacing = preferFrontFacingCamera() ?
      CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;

    CameraSelector cameraSelector = new CameraSelector.Builder()
      .requireLensFacing(lensFacing)
      .build();
    try {
      if (!cameraProvider.hasCamera(cameraSelector)) {
        Log.w(Log.TAG_CAMERA, "No camera with facing: %d", lensFacing);
        cameraSelector = new CameraSelector.Builder().build();
      }
    } catch (CameraInfoUnavailableException e) {
      Log.e(Log.TAG_CAMERA, "Unable to query camera %d", e, lensFacing);
    }

    Preview.Builder previewBuilder = new Preview.Builder()
      .setTargetRotation(getSurfaceRotation())
      .setResolutionSelector(resolutionSelector);
    preview = previewBuilder.build();

    ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
      .setFlashMode(flashMode)
      .setTargetRotation(getSurfaceRotation())
      .setResolutionSelector(resolutionSelector);
    imageCapture = imageCaptureBuilder.build();

    QualitySelector qualitySelector = QualitySelector.fromOrderedList(
      Arrays.asList(Quality.FHD, Quality.HD, Quality.SD),
      FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
    );
    videoCapture = newVideoCapture(qualitySelector, videoAspectRatio);

    boolean needQrScanner = delegate.useQrScanner();
    if (needQrScanner) {
      imageAnalyzer = new ImageAnalysis.Builder()
        .setTargetRotation(getSurfaceRotation())
        .setResolutionSelector(resolutionSelector)
        .build();
      if (cameraQrBridge == null) {
        cameraQrBridge = new CameraQrBridge(this, cameraView);
      }
      CameraQrBridge bridge = cameraQrBridge;
      imageAnalyzer.setAnalyzer(bridge.getBackgroundExecutor(), bridge::processImage);
    } else {
      imageAnalyzer = null;
    }

    try {
      try {
        camera = bindUseCases(cameraSelector);
      } catch (IllegalArgumentException e) {
        if (needQrScanner) {
          throw e;
        }
        Log.w(Log.TAG_CAMERA, "Use case binding with the ratio of videos failed", e);
        videoCapture = newVideoCapture(qualitySelector, AspectRatio.RATIO_DEFAULT);
        camera = bindUseCases(cameraSelector);
      }
      lastCameraState = camera.getCameraInfo().getCameraState();
    } catch (Exception e) {
      Log.e(Log.TAG_CAMERA, "Use case binding failed", e);
      unbindCamera();
      delegate.displayFatalErrorMessage(e.getMessage());
      return;
    }

    setPreferFrontFacingCamera(
      camera.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_FRONT);
    delegate.displayFatalErrorMessage(null);
    delegate.onAvailableCamerasCountChanged(cameraProvider.getAvailableCameraInfos().size());
    onZoomChanged(getCurrentZoom());
    Preview.SurfaceProvider surfaceProvider = cameraView.getSurfaceProvider();
    preview.setSurfaceProvider(surfaceProvider);

    reportFlashMode();
  }

  // @MadeByLLM("gpt-6", id = "upstream-1833")
  private Camera bindUseCases (CameraSelector cameraSelector) {
    UseCaseGroup.Builder group = new UseCaseGroup.Builder().addUseCase(preview);
    if (imageAnalyzer != null) {
      group.addUseCase(imageAnalyzer);
    } else {
      group.addUseCase(imageCapture).addUseCase(videoCapture);
    }
    ViewPort viewPort = cameraView.getViewPort();
    if (viewPort != null) {
      group.setViewPort(viewPort);
    }
    return cameraProvider.bindToLifecycle(
      (LifecycleOwner) context, cameraSelector, group.build());
  }

  private VideoCapture<Recorder> newVideoCapture (QualitySelector qualitySelector, int ratio) {
    Recorder.Builder recorder = new Recorder.Builder()
      .setQualitySelector(qualitySelector);
    if (ratio != AspectRatio.RATIO_DEFAULT) {
      recorder.setAspectRatio(ratio);
    }
    VideoCapture<Recorder> capture = VideoCapture.withOutput(recorder.build());
    capture.setTargetRotation(getSurfaceRotation());
    return capture;
  }

  private void unbindCamera () {
    waitingForPreview = false;
    onFinishOrCancelVideoCapture();
    disableTorch();
    if (imageAnalyzer != null) {
      imageAnalyzer.clearAnalyzer();
    }
    if (cameraProvider != null) {
      ArrayList<UseCase> useCases = new ArrayList<>(4);
      if (preview != null) {
        useCases.add(preview);
      }
      if (imageCapture != null) {
        useCases.add(imageCapture);
      }
      if (videoCapture != null) {
        useCases.add(videoCapture);
      }
      if (imageAnalyzer != null) {
        useCases.add(imageAnalyzer);
      }
      cameraProvider.unbind(useCases.toArray(new UseCase[0]));
    }
    imageAnalyzer = null;
    preview = null;
    imageCapture = null;
    videoCapture = null;
    camera = null;
  }

  @Override
  public void closeCamera () {
    isOpen = false;
    openGeneration++;
    unbindCamera();
    cameraProvider = null;
  }

  // @MadeByLLM("gpt-6.2-sol", id = "camerax-round-handoff")
  public @Nullable LiveData<CameraState> getLastCameraState () {
    return lastCameraState;
  }

  @Override
  protected void onDisplayRotationChanged (int rotation) {
    int surfaceRotation = getSurfaceRotation();
    if (preview != null) {
      preview.setTargetRotation(surfaceRotation);
    }
    if (imageCapture != null) {
      imageCapture.setTargetRotation(surfaceRotation);
    }
    if (videoCapture != null) {
      videoCapture.setTargetRotation(surfaceRotation);
    }
    if (imageAnalyzer != null) {
      imageAnalyzer.setTargetRotation(surfaceRotation);
    }
  }

  private boolean switchingCamera;

  @Override
  public void switchCamera () {
    if (switchingCamera || videoCapturePending) {
      return;
    }
    if (cameraProvider != null) {
      int facing = !preferFrontFacingCamera() ?
        CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
      try {
        long speed = SystemClock.uptimeMillis();
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(facing).build();
        if (!cameraProvider.hasCamera(selector)) {
          Log.w(Log.TAG_CAMERA, "Camera is not available, facing: %d", facing);
          return;
        }
        Log.w("checked camera availability in %dms", SystemClock.uptimeMillis() - speed);
      } catch (CameraInfoUnavailableException e) {
        Log.e(Log.TAG_CAMERA, "Camera info unavailable, facing: %d", e, facing);
        return;
      }
      switchingCamera = true;
      setPreferFrontFacingCamera(!preferFrontFacingCamera());
      delegate.onCameraSourceChange(
        false,
        originalFacing != preferFrontFacingCamera(),
        preferFrontFacingCamera()
      );
      delegate.onResetRenderState(true, () -> {
        bindPreview();
        delegate.onCameraSourceChange(
          true,
          originalFacing != preferFrontFacingCamera(),
          preferFrontFacingCamera()
        );
        switchingCamera = false;
      });
    }
  }

  @Override
  public void switchFlashMode () {
    if (camera != null) {
      if (camera.getCameraInfo().hasFlashUnit()) {
        switch (flashMode) {
          case ImageCapture.FLASH_MODE_AUTO:
            flashMode = ImageCapture.FLASH_MODE_ON;
            break;
          case ImageCapture.FLASH_MODE_ON:
            flashMode = ImageCapture.FLASH_MODE_OFF;
            break;
          case ImageCapture.FLASH_MODE_OFF:
            flashMode = isCapturingVideo() ?
              ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_AUTO;
            break;
          default:
            return;
        }
        imageCapture.setFlashMode(flashMode);
        reportFlashMode();
      } else if (preferFrontFacingCamera()) {
        this.flashMode = this.flashMode == ImageCapture.FLASH_MODE_OFF ?
          ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF;
        reportFlashMode();
      }
    }
  }

  private void reportFlashMode () {
    if (camera != null) {
      int reportFlashMode;
      if (camera.getCameraInfo().hasFlashUnit()) {
        switch (flashMode) {
          case ImageCapture.FLASH_MODE_AUTO:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_AUTO;
            break;
          case ImageCapture.FLASH_MODE_ON:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_ON;
            break;
          case ImageCapture.FLASH_MODE_OFF:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_OFF;
            break;
          default:
            return;
        }
      } else if (preferFrontFacingCamera()) {
        reportFlashMode = flashMode == ImageCapture.FLASH_MODE_OFF ?
          CameraFeatures.FEATURE_FLASH_OFF : CameraFeatures.FEATURE_FLASH_FAKE;
      } else {
        reportFlashMode = CameraFeatures.FEATURE_FLASH_OFF;
      }
      delegate.onFlashModeChanged(reportFlashMode);
    }
  }

  @Override
  protected boolean getBitmap (Bitmap bitmap) {
    for (int i = 0; i < cameraView.getChildCount(); i++) {
      View view = cameraView.getChildAt(i);
      if (view instanceof TextureView) {
        if (((TextureView) view).isAvailable()) {
          ((TextureView) view).getBitmap(bitmap);
          return true;
        }
        return false;
      }
    }
    return false;
  }

  @Override
  public boolean isCameraActive () {
    return isOpen && !isPaused && camera != null;
  }

  @Override
  public float getMaxZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getMaxZoomRatio() : 0f;
  }

  @Override
  public float getMinZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getMinZoomRatio() : 0f;
  }

  @Override
  public float getCurrentZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getZoomRatio() : 0f;
  }

  @Override
  public float getMinZoomStep () {
    return .1f;
  }

  @Override
  protected void onRequestZoom (float zoom) {
    if (camera != null && getCurrentZoom() != zoom) {
      camera.getCameraControl().setZoomRatio(zoom);
      onZoomChanged(zoom);
    }
  }

  @Override
  protected void onResetPreferences () {
    this.flashMode = ImageCapture.FLASH_MODE_OFF;
  }

  @Override
  protected void onTakePhoto (int trimWidth, int trimHeight, int outRotation) {
    if (!isCameraActive() || imageCapture == null) {
      onTakeMediaError(false);
      return;
    }
    final File outFile = getOutputFile(false);
    if (outFile == null) {
      onTakeMediaError(false);
      return;
    }

    boolean ok;
    try {
      ImageCapture.OutputFileOptions.Builder outputBuilder =
        new ImageCapture.OutputFileOptions.Builder(outFile);
      if (preferFrontFacingCamera() &&
        !Settings.instance().getNewSetting(Settings.SETTING_FLAG_CAMERA_NO_FLIP)) {
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        metadata.setReversedHorizontal(true);
        outputBuilder.setMetadata(metadata);
      }
      imageCapture.takePicture(
        outputBuilder.build(),
        ContextCompat.getMainExecutor(context),
        new ImageCapture.OnImageSavedCallback() {
          @Override
          public void onImageSaved (@NonNull ImageCapture.OutputFileResults ignored) {
            U.toGalleryFile(outFile, false, file -> {
              if (file != null) {
                onTakeMediaResult(file, false);
              } else {
                Log.e(Log.TAG_CAMERA, "Output file still does not exist!");
                onTakeMediaError(false);
              }
            });
          }

          @Override
          public void onError (@NonNull ImageCaptureException e) {
            Log.e(Log.TAG_CAMERA, "Cannot take photo", e);
            onTakeMediaError(false);
          }
        }
      );
      ok = true;
    } catch (Throwable t) {
      Log.e(Log.TAG_CAMERA, "Unable to take photo", t);
      onTakeMediaError(false);
      ok = false;
    }
    if (ok) {
      delegate.onPerformSuccessHint(false);
    }
  }

  @Override
  protected boolean onStartVideoCapture (int outRotation) {
    if (!isCameraActive() || videoCapture == null || videoCapturePending ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED) {
      return false;
    }
    File outFile = getOutputFile(true);
    if (outFile == null) {
      return false;
    }
    videoCapturePending = true;
    setFinishingVideo(true);
    try {
      if (flashMode == ImageCapture.FLASH_MODE_ON && camera.getCameraInfo().hasFlashUnit()) {
        forceEnableTorch();
      }
      videoRecording = videoCapture.getOutput()
        .prepareRecording(context, new FileOutputOptions.Builder(outFile).build())
        .withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context), event -> {
          if (event instanceof VideoRecordEvent.Start) {
            setTakingVideo(true, SystemClock.uptimeMillis());
            setFinishingVideo(false);
          } else if (event instanceof VideoRecordEvent.Finalize) {
            videoRecording = null;
            disableTorch();
            setFinishingVideo(true);
            if (isCapturingVideo()) {
              setTakingVideo(false, -1);
            } else {
              delegate.onVideoCaptureEnded();
            }
            VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;
            // These errors can still produce a playable file with the frames already encoded.
            boolean hasUsableOutput = !finalize.hasError() ||
              finalize.getError() == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ||
              finalize.getError() == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE;
            if (hasUsableOutput) {
              U.toGalleryFile(outFile, true, file -> {
                if (file == null) {
                  deleteVideoOutput(outFile);
                }
                ContextCompat.getMainExecutor(context).execute(() -> {
                  videoCapturePending = false;
                  onTakeMediaResult(file, true);
                });
              });
            } else {
              deleteVideoOutput(outFile);
              Log.e(Log.TAG_CAMERA, "Failed to capture video: %d",
                finalize.getCause(), finalize.getError());
              videoCapturePending = false;
              onTakeMediaError(true);
            }
          }
        });
      return true;
    } catch (Exception e) {
      Log.e(Log.TAG_CAMERA, "Cannot start recording video", e);
      disableTorch();
      deleteVideoOutput(outFile);
      videoCapturePending = false;
      setFinishingVideo(false);
      return false;
    }
  }

  private static void deleteVideoOutput (File outFile) {
    if (outFile.exists() && !outFile.delete()) {
      Log.e(Log.TAG_CAMERA, "Unable to delete video output file");
    }
  }

  @Override
  protected void onFinishOrCancelVideoCapture () {
    if (videoRecording != null) {
      setFinishingVideo(true);
      videoRecording.stop();
    }
  }

  private Camera torchCamera;

  private void forceEnableTorch () {
    if (torchCamera == null && camera != null) {
      torchCamera = camera;
      torchCamera.getCameraControl().enableTorch(true);
    }
  }

  private void disableTorch () {
    if (torchCamera != null) {
      torchCamera.getCameraControl().enableTorch(false);
      torchCamera = null;
    }
  }

  @Override
  public void destroy () {
    closeCamera();
    cameraView.getPreviewStreamState().removeObserver(previewStreamObserver);
    if (cameraQrBridge != null) {
      cameraQrBridge.destroy();
      cameraQrBridge = null;
    }
  }

  private boolean isPaused;

  @Override
  public void pauseCamera () {
    isPaused = true;
    unbindCamera();
  }

  @Override
  public void resumeCamera () {
    isPaused = false;
    if (isOpen) {
      bindPreview();
    }
  }
}
// endregion
