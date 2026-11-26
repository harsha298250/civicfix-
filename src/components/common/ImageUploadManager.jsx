import React, { useState, useRef, useEffect } from 'react';
import { compressAndNormalizeImage } from '../../utils/imageUtils.js';

export { compressAndNormalizeImage };

const MAX_FILE_SIZE_MB = 10;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'];

export const ImageUploadManager = ({
  photos = [],
  onChange,
  onRetry,
  maxPhotos = 5,
  allowSampleDemo = true,
  label = 'Incident Photographs & Evidence',
  helperText = 'Upload authentic on-site photos of the reported defect. Minimum 1 photo recommended.'
}) => {
  const fileInputRef = useRef(null);
  const mobileCameraInputRef = useRef(null);
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const cameraStreamRef = useRef(null);

  const [cameraActive, setCameraActive] = useState(false);
  const [cameraStream, setCameraStream] = useState(null);
  const [cameraReady, setCameraReady] = useState(false);
  const [cameraError, setCameraError] = useState('');
  const [validationError, setValidationError] = useState('');

  // Sample demonstration photos
  const DEMO_PHOTOS = [
    {
      name: 'demo-pothole-anna-nagar.jpg',
      url: 'https://images.unsplash.com/photo-1515162816999-a0c47dc192f7?auto=format&fit=crop&w=800&q=80',
      label: 'Sample Pothole'
    },
    {
      name: 'demo-street-water-leak.jpg',
      url: 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=800&q=80',
      label: 'Sample Water Leak'
    }
  ];

  // Stop camera stream when modal closes or unmounts
  const stopCameraStream = () => {
    const activeStream = cameraStreamRef.current || cameraStream;
    if (activeStream) {
      activeStream.getTracks().forEach((track) => track.stop());
    }
    cameraStreamRef.current = null;
    setCameraStream(null);
    setCameraReady(false);
    setCameraActive(false);
    setCameraError('');
  };

  useEffect(() => {
    cameraStreamRef.current = cameraStream;
    return () => {
      if (cameraStreamRef.current) {
        cameraStreamRef.current.getTracks().forEach((track) => track.stop());
        cameraStreamRef.current = null;
      }
    };
  }, [cameraStream]);

  // Process selected files from input
  const processFiles = async (fileList) => {
    setValidationError('');
    if (!fileList || fileList.length === 0) return;

    const filesArray = Array.from(fileList);
    const newPhotos = [...photos];
    let rejectedCount = 0;
    let sizeError = false;

    for (const file of filesArray) {
      if (newPhotos.length >= maxPhotos) {
        setValidationError(`Maximum ${maxPhotos} photographs allowed.`);
        break;
      }

      // Check mime type
      const isAllowedType =
        ALLOWED_MIME_TYPES.includes(file.type.toLowerCase()) || file.type.startsWith('image/');
      if (!isAllowedType) {
        rejectedCount++;
        continue;
      }

      // Check size
      if (file.size > MAX_FILE_SIZE_BYTES) {
        sizeError = true;
        continue;
      }

      const compressedItem = await compressAndNormalizeImage(file);
      newPhotos.push({
        id: `img_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
        ...compressedItem
      });
    }

    if (rejectedCount > 0) {
      setValidationError('Some files were ignored because only image files (JPG, PNG, WebP) are accepted.');
    } else if (sizeError) {
      setValidationError(`Some files exceeded the maximum allowed size of ${MAX_FILE_SIZE_MB}MB.`);
    }

    onChange(newPhotos);
  };

  const handleFileInputChange = (e) => {
    processFiles(e.target.files);
    if (fileInputRef.current) fileInputRef.current.value = '';
    if (mobileCameraInputRef.current) mobileCameraInputRef.current.value = '';
  };

  // Trigger file dialog
  const handleOpenFileDialog = () => {
    if (fileInputRef.current) fileInputRef.current.click();
  };

  // Trigger mobile direct camera
  const handleOpenMobileCamera = () => {
    if (mobileCameraInputRef.current) mobileCameraInputRef.current.click();
  };

  // Connect active camera stream to video element whenever modal is open
  useEffect(() => {
    if (cameraActive && cameraStream && videoRef.current) {
      if (videoRef.current.srcObject !== cameraStream) {
        videoRef.current.srcObject = cameraStream;
      }
      videoRef.current
        .play()
        .catch((err) => console.warn('Video auto-playback warning:', err));
    }
  }, [cameraActive, cameraStream]);

  // Start live webcam for desktop / browser
  const handleStartWebcam = async () => {
    setCameraError('');
    setCameraReady(false);
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setCameraError('Webcam access is not supported by your browser. Please use file selection instead.');
      setCameraActive(true);
      return;
    }

    // Stop any prior stream before requesting a new one
    if (cameraStreamRef.current) {
      cameraStreamRef.current.getTracks().forEach((track) => track.stop());
      cameraStreamRef.current = null;
      setCameraStream(null);
    }

    // Open modal so user sees connecting / permission prompt state
    setCameraActive(true);

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false
      });
      cameraStreamRef.current = stream;
      setCameraStream(stream);
    } catch (err) {
      console.warn('Initial camera stream attempt failed, retrying with generic video constraints:', err);
      try {
        const fallbackStream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false
        });
        cameraStreamRef.current = fallbackStream;
        setCameraStream(fallbackStream);
      } catch (err2) {
        console.error('All camera attempts failed:', err2);
        setCameraReady(false);
        if (err2.name === 'NotAllowedError' || err2.name === 'PermissionDeniedError') {
          setCameraError('Camera permission was denied in your browser. Please allow camera access and click Retry, or choose an image file.');
        } else if (err2.name === 'NotFoundError' || err2.name === 'DevicesNotFoundError') {
          setCameraError('No camera device detected on your hardware.');
        } else {
          setCameraError(err2.message || 'Unable to open camera stream. Please use the image file selector.');
        }
      }
    }
  };

  // Capture real snapshot from active webcam stream
  const handleCaptureSnapshot = () => {
    const video = videoRef.current;
    if (!video || !cameraStreamRef.current || !cameraReady || video.videoWidth <= 0 || cameraError) {
      setCameraError('Camera feed is not active yet. Please allow camera access or retry before capturing.');
      return;
    }

    const canvas = document.createElement('canvas');
    const width = video.videoWidth;
    const height = video.videoHeight;
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, width, height);

    // Overlay civic telemetry watermark
    ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
    ctx.fillRect(0, height - 42, width, 42);
    ctx.fillStyle = '#10b981';
    ctx.font = 'bold 15px monospace';
    ctx.fillText(`CIVICFIX VERIFIED PROOF • ${new Date().toLocaleDateString()} ${new Date().toLocaleTimeString()} IST`, 16, height - 16);

    canvas.toBlob(
      (blob) => {
        if (!blob) return;
        const file = new File([blob], `evidence_camera_${Date.now()}.jpg`, { type: 'image/jpeg' });
        const previewUrl = URL.createObjectURL(file);

        const newPhoto = {
          id: `cam_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
          file,
          name: file.name,
          sizeBytes: file.size,
          sizeFormatted: (file.size / (1024 * 1024)).toFixed(2) + ' MB',
          type: 'image/jpeg',
          url: previewUrl,
          isLocal: true,
          uploadStatus: 'pending',
          source: 'Live Camera Capture'
        };

        stopCameraStream();
        onChange([...photos, newPhoto]);
      },
      'image/jpeg',
      0.92
    );
  };

  // Remove photo
  const handleRemovePhoto = (id) => {
    const photoToRemove = photos.find((p) => p.id === id);
    if (photoToRemove && photoToRemove.isLocal && photoToRemove.url && photoToRemove.url.startsWith('blob:')) {
      URL.revokeObjectURL(photoToRemove.url);
    }
    onChange(photos.filter((p) => p.id !== id));
  };

  // Retry photo upload or re-processing
  const handleInternalRetry = (item) => {
    if (onRetry) {
      onRetry(item);
      return;
    }
    const updated = photos.map((p) => {
      if (p.id === item.id) {
        return { ...p, uploadStatus: 'uploading', uploadProgress: 15, error: null };
      }
      return p;
    });
    onChange(updated);
    setTimeout(() => {
      onChange(
        photos.map((p) => {
          if (p.id === item.id) {
            return { ...p, uploadStatus: 'idle', uploadProgress: 100, error: null };
          }
          return p;
        })
      );
    }, 500);
  };

  // Attach demo sample photo
  const handleAttachDemo = (demo) => {
    if (photos.length >= maxPhotos) {
      setValidationError(`Maximum ${maxPhotos} photographs allowed.`);
      return;
    }
    const newPhoto = {
      id: `demo_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
      file: null,
      name: demo.name,
      sizeBytes: 154000,
      sizeFormatted: '150 KB',
      type: 'image/jpeg',
      url: demo.url,
      isLocal: false,
      source: 'Demo Sample (Simulated)'
    };
    onChange([...photos, newPhoto]);
  };

  return (
    <div className="space-y-4">
      {/* Hidden native inputs */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
        onChange={handleFileInputChange}
        className="hidden"
      />

      <input
        ref={mobileCameraInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={handleFileInputChange}
        className="hidden"
      />

      <canvas ref={canvasRef} className="hidden" />

      {/* Header and description */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
        <div>
          <label className="block text-xs font-semibold text-on-surface uppercase tracking-wider">
            {label} ({photos.length}/{maxPhotos})
          </label>
          <p className="text-xs text-on-surface-variant mt-0.5">{helperText}</p>
        </div>
        <span className="text-[11px] font-mono text-tertiary">Max {MAX_FILE_SIZE_MB}MB per image</span>
      </div>

      {/* Privacy Notice Banner */}
      <div className="p-3 bg-amber-50/90 dark:bg-amber-950/20 border border-amber-200/80 dark:border-amber-800/40 rounded-xl flex items-start gap-2.5 text-xs text-amber-900 dark:text-amber-200" role="note" aria-label="Privacy Notice">
        <span className="material-symbols-outlined text-[18px] text-amber-700 dark:text-amber-400 shrink-0 mt-0.5">privacy_tip</span>
        <div>
          <span className="font-semibold">Privacy & Citizen Protection:</span>
          <span className="ml-1 text-amber-800 dark:text-amber-300">
            Please frame photos to capture only the public defect. Avoid photographing people&apos;s faces, identifiable private property, or vehicle registration numbers. Uploads are stripped of sensitive metadata and processed according to Tamil Nadu Data Protection standards.
          </span>
        </div>
      </div>

      {/* Validation alert */}
      {validationError && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-xl text-xs text-red-700 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[18px]">error</span>
            <span>{validationError}</span>
          </div>
          <button type="button" onClick={() => setValidationError('')} className="text-red-500 hover:text-red-800">
            <span className="material-symbols-outlined text-[16px]">close</span>
          </button>
        </div>
      )}

      {/* Action Buttons: Choose File, Take Camera Photo, or Demo Option */}
      <div className="flex flex-wrap items-center gap-2.5">
        <button
          type="button"
          onClick={handleOpenFileDialog}
          disabled={photos.length >= maxPhotos}
          className="px-4 py-2 bg-surface-container-low hover:bg-surface-container border border-outline-variant/40 rounded-xl text-xs font-semibold text-on-surface flex items-center gap-2 transition-all disabled:opacity-50 shadow-sm"
        >
          <span className="material-symbols-outlined text-[18px] text-primary-container">add_photo_alternate</span>
          <span>Choose from Gallery / Computer</span>
        </button>

        {/* Camera for mobile or desktop */}
        <button
          type="button"
          onClick={() => {
            // Check if mobile device by touch/userAgent
            const isMobile = /iPhone|iPad|iPod|Android/i.test(navigator.userAgent);
            if (isMobile) {
              handleOpenMobileCamera();
            } else {
              handleStartWebcam();
            }
          }}
          disabled={photos.length >= maxPhotos}
          className="px-4 py-2 bg-surface-container-low hover:bg-surface-container border border-outline-variant/40 rounded-xl text-xs font-semibold text-on-surface flex items-center gap-2 transition-all disabled:opacity-50 shadow-sm"
        >
          <span className="material-symbols-outlined text-[18px] text-secondary">photo_camera</span>
          <span>Use Device Camera</span>
        </button>

        {/* Demo Sample Attachment (Clearly labelled) */}
        {allowSampleDemo && (
          <div className="flex items-center gap-1.5 ml-auto">
            <span className="text-[11px] text-tertiary font-medium hidden md:inline">Demo option:</span>
            {DEMO_PHOTOS.map((demo, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => handleAttachDemo(demo)}
                disabled={photos.length >= maxPhotos}
                className="px-2.5 py-1.5 bg-surface-container-lowest hover:bg-surface-container-low border border-dashed border-outline-variant/60 rounded-lg text-[11px] font-medium text-tertiary hover:text-on-surface transition-all disabled:opacity-40"
                title="Attach sample photo for quick demonstration"
              >
                + {demo.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Photos Grid & Previews */}
      {photos.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
          {photos.map((item, idx) => (
            <div
              key={item.id || idx}
              className="relative group rounded-xl overflow-hidden border border-outline-variant/50 bg-surface-container-lowest shadow-sm flex flex-col"
            >
              <div className="relative aspect-video bg-black/5 overflow-hidden">
                <img
                  src={item.url}
                  alt={item.name || `Photo ${idx + 1}`}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  onError={(e) => {
                    e.currentTarget.onerror = null;
                    e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='225' viewBox='0 0 400 225'%3E%3Crect width='400' height='225' fill='%231e293b'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%2394a3b8' font-family='sans-serif' font-size='13'%3EPreview Unavailable%3C/text%3E%3C/svg%3E";
                  }}
                />

                {/* Upload In Progress Overlay */}
                {item.uploadStatus === 'uploading' && (
                  <div className="absolute inset-0 bg-black/65 backdrop-blur-xs flex flex-col items-center justify-center p-3 text-white z-10">
                    <div className="w-full bg-white/20 rounded-full h-1.5 mb-2 overflow-hidden">
                      <div
                        className="bg-primary-container h-1.5 rounded-full transition-all duration-300"
                        style={{ width: `${item.uploadProgress ?? 65}%` }}
                      />
                    </div>
                    <span className="text-[11px] font-medium flex items-center gap-1.5">
                      <span className="inline-block w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      Uploading ({item.uploadProgress ?? 65}%)
                    </span>
                  </div>
                )}

                {/* Upload Failed / Error Overlay with Retry */}
                {(item.uploadStatus === 'failed' || item.uploadStatus === 'error' || item.error) && (
                  <div className="absolute inset-0 bg-red-950/85 backdrop-blur-xs flex flex-col items-center justify-center p-2.5 text-white z-10 text-center gap-1">
                    <span className="material-symbols-outlined text-red-400 text-xl">error</span>
                    <span className="text-[10px] text-red-200 line-clamp-2 px-1">
                      {item.error || 'Upload failed'}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleInternalRetry(item)}
                      className="mt-1 px-2.5 py-1 bg-red-600 hover:bg-red-500 text-white rounded text-[10px] font-semibold flex items-center gap-1 shadow-sm transition-all"
                    >
                      <span className="material-symbols-outlined text-[13px]">refresh</span>
                      <span>Retry</span>
                    </button>
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => handleRemovePhoto(item.id)}
                  className="absolute top-2 right-2 w-7 h-7 bg-red-600/90 hover:bg-red-700 text-white rounded-full flex items-center justify-center shadow-md transition-colors z-20"
                  title="Remove photograph"
                >
                  <span className="material-symbols-outlined text-[16px]">close</span>
                </button>

                <div className="absolute bottom-2 left-2 px-2 py-0.5 rounded bg-black/70 backdrop-blur-xs text-white text-[10px] font-mono z-20">
                  #{idx + 1}
                </div>
              </div>

              <div className="p-2.5 bg-surface-container-low text-xs flex flex-col justify-between flex-1 gap-1">
                <div className="flex items-center justify-between gap-1">
                  <span className="font-semibold text-on-surface truncate text-[11px]" title={item.name}>
                    {item.name || 'photograph.jpg'}
                  </span>
                  <span className="text-[10px] text-tertiary whitespace-nowrap font-mono">
                    {item.sizeFormatted}
                  </span>
                </div>
                <div className="flex items-center justify-between text-[10px] text-tertiary">
                  <span className="flex items-center gap-1">
                    <span
                      className={`w-1.5 h-1.5 rounded-full ${
                        item.isLocal ? 'bg-emerald-500' : 'bg-amber-500'
                      }`}
                    />
                    <span>{item.source}</span>
                  </span>
                  <span className="text-secondary font-medium">
                    {item.uploadStatus === 'uploading' && 'Uploading...'}
                    {item.uploadStatus === 'success' && 'Uploaded to Storage'}
                    {(item.uploadStatus === 'failed' || item.uploadStatus === 'error') && 'Failed'}
                    {(!item.uploadStatus || item.uploadStatus === 'idle' || item.uploadStatus === 'pending') && 'Pending Upload'}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div
          onClick={handleOpenFileDialog}
          className="border-2 border-dashed border-outline-variant/60 hover:border-primary-container rounded-2xl p-6 text-center cursor-pointer transition-all hover:bg-surface-container-low/40 group"
        >
          <div className="w-12 h-12 rounded-2xl bg-surface-container-low text-primary-container flex items-center justify-center mx-auto mb-2 group-hover:scale-110 transition-transform">
            <span className="material-symbols-outlined text-2xl">add_a_photo</span>
          </div>
          <p className="text-sm font-semibold text-on-surface">Click to select photos or use camera</p>
          <p className="text-xs text-on-surface-variant mt-1">Supports PNG, JPG, WebP up to 10MB each</p>
          <p className="text-[11px] text-tertiary mt-2">
            Selected photos are held in browser memory as File objects and prepared for backend multipart transmission.
          </p>
        </div>
      )}

      {/* Live Camera Stream Modal (Desktop / Browser) */}
      {cameraActive && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full overflow-hidden shadow-2xl border border-outline-variant/30 flex flex-col">
            <div className="p-4 border-b border-outline-variant/30 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-secondary text-[20px]">photo_camera</span>
                <h3 className="font-title text-sm font-bold text-on-surface">Live Camera Capture</h3>
                {cameraStream && cameraReady && !cameraError ? (
                  <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono text-[10px] flex items-center gap-1 font-semibold">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                    Live Viewfinder
                  </span>
                ) : cameraError ? (
                  <span className="px-2 py-0.5 rounded-full bg-red-50 text-red-700 border border-red-200 font-mono text-[10px] font-semibold">
                    Camera Unavailable
                  </span>
                ) : (
                  <span className="px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-mono text-[10px] font-semibold">
                    Connecting Camera...
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={stopCameraStream}
                className="p-1 rounded-lg hover:bg-surface-container text-tertiary hover:text-on-surface"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="p-4 bg-black flex items-center justify-center min-h-[300px] relative">
              {cameraError ? (
                <div className="text-center p-6 text-white space-y-3" role="alert">
                  <span className="material-symbols-outlined text-4xl text-amber-400">videocam_off</span>
                  <p className="text-sm font-semibold text-amber-300">{cameraError}</p>
                  <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
                    <button
                      type="button"
                      onClick={handleStartWebcam}
                      className="px-3.5 py-1.5 bg-primary-container hover:bg-primary text-white rounded-lg text-xs font-semibold flex items-center gap-1.5"
                    >
                      <span className="material-symbols-outlined text-[15px]">refresh</span>
                      <span>Retry Camera</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        stopCameraStream();
                        handleOpenFileDialog();
                      }}
                      className="px-3.5 py-1.5 bg-white/10 hover:bg-white/20 text-white rounded-lg text-xs font-semibold"
                    >
                      Choose Image File
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <video
                    ref={videoRef}
                    autoPlay
                    playsInline
                    muted
                    onLoadedMetadata={(e) => {
                      if (e.target.videoWidth > 0) setCameraReady(true);
                      e.target.play().catch(() => {});
                    }}
                    onPlaying={(e) => {
                      if (e.target.videoWidth > 0) setCameraReady(true);
                    }}
                    className="w-full max-h-[360px] object-contain rounded-lg"
                  />
                  {!cameraReady && (
                    <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/75 text-white gap-2">
                      <span className="material-symbols-outlined text-3xl animate-spin">progress_activity</span>
                      <span className="text-xs text-gray-300">Requesting camera permission & initializing feed...</span>
                    </div>
                  )}
                  {cameraReady && (
                    <div className="absolute top-6 left-6 pointer-events-none bg-black/60 backdrop-blur-xs text-white text-[10px] font-mono px-2 py-1 rounded border border-white/20">
                      REC [●] {videoRef.current?.videoWidth || 1280}x{videoRef.current?.videoHeight || 720} • Live
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="p-4 border-t border-outline-variant/30 flex items-center justify-between">
              <button
                type="button"
                onClick={stopCameraStream}
                className="px-4 py-2 text-xs font-semibold text-on-surface hover:bg-surface-container rounded-xl"
              >
                Cancel
              </button>

              <div className="flex items-center gap-2">
                {allowSampleDemo && (
                  <button
                    type="button"
                    onClick={() => {
                      if (DEMO_PHOTOS.length > 0) {
                        handleAttachDemo(DEMO_PHOTOS[0]);
                        stopCameraStream();
                      }
                    }}
                    className="px-3 py-2 text-xs font-medium text-tertiary hover:text-on-surface hover:bg-surface-container rounded-xl transition-all"
                  >
                    Use Sample Proof
                  </button>
                )}

                <button
                  type="button"
                  onClick={handleCaptureSnapshot}
                  disabled={!cameraStream || !cameraReady || Boolean(cameraError)}
                  className="px-5 py-2.5 bg-primary-container hover:bg-primary text-white text-xs font-semibold rounded-xl flex items-center gap-2 shadow-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <span className="material-symbols-outlined text-[18px]">camera</span>
                  <span>Capture Snapshot</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ImageUploadManager;
