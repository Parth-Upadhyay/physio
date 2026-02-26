# Ped - AI Fitness Assistant

Ped is an Android application that uses computer vision to track physical exercises in real-time. By leveraging Google MediaPipe's Pose Landmarker, the app provides instant feedback on form and counts repetitions for various exercises.

## Features

### Dashboard
- Daily activity summary showing total repetitions for each exercise type.
- Exercise selection interface to start new workout sessions.
- Session-based persistence for tracking progress during the current app usage.

### Exercise Tracking
- **Bicep Curls**: Tracks arm extension and flexion. Detects full range of motion and provides feedback on curl depth and extension.
- **Air Squats**: Monitors hip and knee angles to ensure proper depth (parallel to floor) and full standing position.
- **Real-time Skeleton Overlay**: Displays a skeletal representation of the user's pose to confirm accurate tracking.
- **Smart Rep Counting**: Includes cooldown logic to prevent double-counting and ensures only high-quality repetitions are recorded.

### Feedback System
- Visual feedback on the screen guides the user through each repetition.
- Status messages like "Lower your hips" or "Good depth! Now stand up" help maintain proper form.

## Technical Details

- **Pose Estimation**: Powered by MediaPipe Tasks Vision.
- **UI Framework**: Built entirely with Jetpack Compose for a modern, reactive interface.
- **Camera**: Integrated using CameraX for efficient frame capture and processing.
- **Analyzer Engine**: Custom logic for calculating joint angles and state machine transitions for rep counting.

## Setup Instructions

1. Clone the repository.
2. Ensure you have the latest version of Android Studio installed.
3. Download the `pose_landmarker_full.task` model file from the MediaPipe website and place it in the `app/src/main/assets/` directory.
4. Build the project using Gradle.
5. Deploy to an Android device with camera support.

## Project Structure

- `MainActivity.kt`: Handles the UI navigation, camera setup, and dashboard logic.
- `ExerciseAnalyzer.kt`: The core engine for pose analysis and repetition logic.
- `ui/components/`: Reusable Compose components like `CameraPreview` and `SkeletonOverlay`.
- `ui/theme/`: Standard Material 3 theme configuration.
