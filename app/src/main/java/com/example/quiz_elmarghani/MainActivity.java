package com.example.quiz_elmarghani;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private TextView tvQuestion, tvQuestionCount, tvScore, tvLocation;
    private Button btnOptionA, btnOptionB, btnOptionC, btnOptionD;
    private PreviewView previewView;
    
    private List<Question> questionList;
    private int currentQuestionIndex = 0;
    private int score = 0;

    private FusedLocationProviderClient fusedLocationClient;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;

    private MediaRecorder mediaRecorder;
    private Handler mainHandler = new Handler();
    private boolean isMonitoringAudio = false;
    private boolean isCameraRunning = false;
    private boolean isMonitoringEnabled = false; 
    private long lastFraudTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initViews();
        setupFirebaseAndSensors();
        loadQuestions();
        updateQuestion();

        if (checkPermissions()) {
            startMonitoring();
        } else {
            requestPermissions();
        }
    }

    private void initViews() {
        tvQuestion = findViewById(R.id.tvQuestion);
        tvQuestionCount = findViewById(R.id.tvQuestionCount);
        tvScore = findViewById(R.id.tvScore);
        tvLocation = findViewById(R.id.tvLocation);
        btnOptionA = findViewById(R.id.btnOptionA);
        btnOptionB = findViewById(R.id.btnOptionB);
        btnOptionC = findViewById(R.id.btnOptionC);
        btnOptionD = findViewById(R.id.btnOptionD);
        previewView = findViewById(R.id.previewView);

        btnOptionA.setOnClickListener(v -> checkAnswer("A"));
        btnOptionB.setOnClickListener(v -> checkAnswer("B"));
        btnOptionC.setOnClickListener(v -> checkAnswer("C"));
        btnOptionD.setOnClickListener(v -> checkAnswer("D"));
    }

    private void setupFirebaseAndSensors() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        faceDetector = FaceDetection.getClient(options);
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA, 
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.RECORD_AUDIO
                },
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startMonitoring(); 
        }
    }

    private void startMonitoring() {
        getLocation();
        startCamera();
        startAudioMonitoring();
        
        // On attend que l'utilisateur soit bien en place avant d'activer la détection de fraude
        mainHandler.postDelayed(() -> {
            if (!isFinishing()) {
                isMonitoringEnabled = true;
                Toast.makeText(this, "Surveillance anti-fraude activée", Toast.LENGTH_SHORT).show();
            }
        }, 8000); // 8 secondes de marge
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                tvLocation.setText("📍 Localisation: " + String.format("%.4f", location.getLatitude()) + ", " + String.format("%.4f", location.getLongitude()));
            }
        });
    }

    private void startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e("QuizLog", "Erreur CameraX: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Essayer d'abord la caméra frontale, sinon la caméra arrière (utile pour les émulateurs)
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

        try {
            cameraProvider.unbindAll();
            
            if (!cameraProvider.hasCamera(cameraSelector)) {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            }

            if (cameraProvider.hasCamera(cameraSelector)) {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                isCameraRunning = true;
                Log.d("QuizLog", "Caméra démarrée avec succès");
            } else {
                Log.e("QuizLog", "Aucune caméra disponible");
                isCameraRunning = false;
            }
        } catch (Exception e) {
            Log.e("QuizLog", "Binding caméra échoué: " + e.getMessage());
            isCameraRunning = false;
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processImageProxy(ImageProxy imageProxy) {
        if (!isMonitoringEnabled || !isCameraRunning || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty() && isMonitoringEnabled) {
                        runOnUiThread(() -> triggerFraud("Aucun visage détecté !"));
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void startAudioMonitoring() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;

        try {
            stopAudioMonitoring(); 
            mediaRecorder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? new MediaRecorder(this) : new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(new File(getCacheDir(), "audio_check.3gp").getAbsolutePath());

            mediaRecorder.prepare();
            mediaRecorder.start();
            isMonitoringAudio = true;
            checkAudioLevel();
        } catch (Exception e) {
            Log.e("QuizLog", "Micro indisponible : " + e.getMessage());
            isMonitoringAudio = false;
        }
    }

    private void checkAudioLevel() {
        if (!isMonitoringAudio || mediaRecorder == null) return;
        try {
            int amplitude = mediaRecorder.getMaxAmplitude();
            if (isMonitoringEnabled && amplitude > 25000) { 
                runOnUiThread(() -> triggerFraud("Bruit suspect détecté !"));
            }
            mainHandler.postDelayed(this::checkAudioLevel, 1000);
        } catch (Exception e) {
            isMonitoringAudio = false;
        }
    }

    private void triggerFraud(String reason) {
        if (!isMonitoringEnabled) return;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFraudTime < 5000) return; 
        
        lastFraudTime = currentTime;
        Toast.makeText(this, "⚠️ FRAUDE : " + reason, Toast.LENGTH_LONG).show();
        restartQuiz();
    }

    private void restartQuiz() {
        currentQuestionIndex = 0;
        score = 0;
        updateQuestion();
    }

    private void loadQuestions() {
        questionList = new ArrayList<>();
        questionList.add(new Question("1. Quelle est la capitale de la France ?", "Madrid", "Paris", "Rome", "Berlin", "B"));
        questionList.add(new Question("2. Combien y a-t-il de continents ?", "5", "6", "7", "8", "C"));
        questionList.add(new Question("3. Quelle planète est la plus proche du Soleil ?", "Mars", "Vénus", "Mercure", "Jupiter", "C"));
        questionList.add(new Question("4. Qui a peint La Joconde ?", "Picasso", "Van Gogh", "Léonard de Vinci", "Monet", "C"));
        questionList.add(new Question("5. Quel est le plus grand océan du monde ?", "Atlantique", "Indien", "Arctique", "Pacifique", "D"));
        questionList.add(new Question("6. Quelle est la monnaie du Japon ?", "Dollar", "Yen", "Euro", "Won", "B"));
        questionList.add(new Question("7. Combien de joueurs y a-t-il dans une équipe de football ?", "9", "10", "11", "12", "C"));
        questionList.add(new Question("8. Quel gaz les humains respirent-ils principalement ?", "Azote", "Dioxyde de carbone", "Oxygène", "Hélium", "C"));
        questionList.add(new Question("9. Quel est le symbole chimique de l’or ?", "Ag", "Au", "Fe", "O", "B"));
        questionList.add(new Question("10. Dans quel pays se trouvent les pyramides de Gizeh ?", "Maroc", "Mexique", "Égypte", "Inde", "C"));
        questionList.add(new Question("11. Qui a découvert la gravité ?", "Einstein", "Newton", "Galilée", "Tesla", "B"));
        questionList.add(new Question("12. Quel est l’animal le plus rapide du monde ?", "Lion", "Aigle", "Guépard", "Tigre", "C"));
        questionList.add(new Question("13. Quelle est la capitale du Canada ?", "Toronto", "Ottawa", "Montréal", "Vancouver", "B"));
        questionList.add(new Question("14. Quelle langue est la plus parlée au monde ?", "Français", "Anglais", "Espagnol", "Mandarin", "D"));
        questionList.add(new Question("15. Combien de jours compte une année normale ?", "360", "364", "365", "366", "C"));
        questionList.add(new Question("16. Quel instrument possède 88 touches ?", "Guitare", "Piano", "Violon", "Flûte", "B"));
        questionList.add(new Question("17. Quel est le plus grand mammifère du monde ?", "Éléphant", "Requin", "Baleine bleue", "Girafe", "C"));
        questionList.add(new Question("18. En informatique, que signifie “WWW” ?", "World Wide Web", "Web World Wide", "Wide World Web", "World Web Wide", "A"));
        questionList.add(new Question("19. Quel pays a gagné la Coupe du Monde de la FIFA 2022 ?", "France", "Brésil", "Argentine", "Allemagne", "C"));
        questionList.add(new Question("20. Quelle est la formule chimique de l’eau ?", "CO2", "O2", "H2O", "NaCl", "C"));
        questionList.add(new Question("21. Qui a écrit Harry Potter ?", "Tolkien", "J.K. Rowling", "Victor Hugo", "Stephen King", "B"));
        questionList.add(new Question("22. Quel continent est le plus grand ?", "Europe", "Afrique", "Asie", "Amérique", "C"));
        questionList.add(new Question("23. Quel sport utilise un volant ?", "Tennis", "Basketball", "Badminton", "Baseball", "C"));
        questionList.add(new Question("24. Quelle est la capitale du Maroc ?", "Casablanca", "Marrakech", "Rabat", "Fès", "C"));
        questionList.add(new Question("25. Quel est le métal liquide à température ambiante ?", "Fer", "Aluminium", "Mercure", "Cuivre", "C"));
        questionList.add(new Question("26. Combien y a-t-il de couleurs dans un arc-en-ciel ?", "5", "6", "7", "8", "C"));
        questionList.add(new Question("27. Quel est le plus long fleuve du monde ?", "Amazone", "Nil", "Mississippi", "Congo", "B"));
        questionList.add(new Question("28. Qui était le premier homme à marcher sur la Lune ?", "Yuri Gagarine", "Buzz Aldrin", "Neil Armstrong", "Elon Musk", "C"));
        questionList.add(new Question("29. Quel pays est surnommé “le pays du soleil levant” ?", "Chine", "Thaïlande", "Corée du Sud", "Japon", "D"));
        questionList.add(new Question("30. Quelle est la vitesse approximative de la lumière ?", "30 000 km/s", "300 000 km/s", "3 000 km/s", "3 000 000 km/s", "B"));
    }

    private void updateQuestion() {
        if (currentQuestionIndex < questionList.size()) {
            Question q = questionList.get(currentQuestionIndex);
            tvQuestion.setText(q.getQuestion());
            btnOptionA.setText("A) " + q.getOptionA());
            btnOptionB.setText("B) " + q.getOptionB());
            btnOptionC.setText("C) " + q.getOptionC());
            btnOptionD.setText("D) " + q.getOptionD());
            tvQuestionCount.setText("Question " + (currentQuestionIndex + 1) + " / " + questionList.size());
            tvScore.setText("Score: " + score);
        } else {
            showResult();
        }
    }

    private void checkAnswer(String selectedOption) {
        if (questionList.get(currentQuestionIndex).getCorrectAnswer().equals(selectedOption)) {
            score++;
            Toast.makeText(this, "✅ Correct !", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "❌ Faux !", Toast.LENGTH_SHORT).show();
        }
        currentQuestionIndex++;
        updateQuestion();
    }

    private void showResult() {
        isMonitoringEnabled = false;
        stopAudioMonitoring();
        new AlertDialog.Builder(this)
                .setTitle("🏆 Résultat Final")
                .setMessage("Félicitations ! Votre score est de : " + score + " / " + questionList.size())
                .setPositiveButton("Recommencer", (dialog, which) -> {
                    restartQuiz();
                    isMonitoringEnabled = true;
                    startAudioMonitoring();
                })
                .setNegativeButton("Quitter", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void stopAudioMonitoring() {
        isMonitoringAudio = false;
        mainHandler.removeCallbacksAndMessages(null);
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isMonitoringEnabled = false;
        cameraExecutor.shutdown();
        if (faceDetector != null) faceDetector.close();
        stopAudioMonitoring();
    }
}