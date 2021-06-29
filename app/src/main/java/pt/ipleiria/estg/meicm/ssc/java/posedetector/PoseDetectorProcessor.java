package pt.ipleiria.estg.meicm.ssc.poses.java.posedetector;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import pt.ipleiria.estg.meicm.ssc.poses.AppData;
import pt.ipleiria.estg.meicm.ssc.poses.GMailSender;
import pt.ipleiria.estg.meicm.ssc.poses.GraphicOverlay;
import pt.ipleiria.estg.meicm.ssc.poses.java.VisionProcessorBase;
import pt.ipleiria.estg.meicm.ssc.poses.java.posedetector.classification.PoseClassifierProcessor;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import static kotlin.random.RandomKt.Random;

/** A processor to run pose detector. */
public class PoseDetectorProcessor
    extends VisionProcessorBase<PoseDetectorProcessor.PoseWithClassification> {
  private static final String TAG = "PoseDetectorProcessor";

  private final PoseDetector detector;

  private final boolean showInFrameLikelihood;
  private final boolean visualizeZ;
  private final boolean rescaleZForVisualization;
  private final boolean runClassification;
  private final boolean isStreamMode;
  private final Context context;
  private final Executor classificationExecutor;

  private PoseClassifierProcessor poseClassifierProcessor;
  /** Internal class to hold Pose and classification results. */
  protected static class PoseWithClassification {
    private final Pose pose;
    private final List<String> classificationResult;

    public PoseWithClassification(Pose pose, List<String> classificationResult) {
      this.pose = pose;
      this.classificationResult = classificationResult;
    }

    public Pose getPose() {
      return pose;
    }

    public List<String> getClassificationResult() {
      return classificationResult;
    }
  }

  public PoseDetectorProcessor(
      Context context,
      PoseDetectorOptionsBase options,
      boolean showInFrameLikelihood,
      boolean visualizeZ,
      boolean rescaleZForVisualization,
      boolean runClassification,
      boolean isStreamMode) {
    super(context);
    this.showInFrameLikelihood = showInFrameLikelihood;
    this.visualizeZ = visualizeZ;
    this.rescaleZForVisualization = rescaleZForVisualization;
    detector = PoseDetection.getClient(options);
    this.runClassification = runClassification;
    this.isStreamMode = isStreamMode;
    this.context = context;
    classificationExecutor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void stop() {
    super.stop();
    detector.close();
  }

  @Override
  protected Task<PoseWithClassification> detectInImage(InputImage image) {
    return detector
        .process(image)
        .continueWith(
            classificationExecutor,
            task -> {
              Pose pose = task.getResult();
              List<String> classificationResult = new ArrayList<>();
              if (runClassification) {
                if (poseClassifierProcessor == null) {
                  poseClassifierProcessor = new PoseClassifierProcessor(context, isStreamMode);
                }
                classificationResult = poseClassifierProcessor.getPoseResult(pose);

              }
              return new PoseWithClassification(pose, classificationResult);
            });
  }

  @Override
  protected void onSuccess(
      @NonNull PoseWithClassification poseWithClassification,
      @NonNull GraphicOverlay graphicOverlay) {
    graphicOverlay.add(
        new PoseGraphic(
            graphicOverlay,
            poseWithClassification.pose,
            showInFrameLikelihood,
            visualizeZ,
            rescaleZForVisualization,
            poseWithClassification.classificationResult));

    if (poseWithClassification.classificationResult.size() > 1){
        String className = poseWithClassification.classificationResult.get(1);
        String[] classNameD = className.split(":",2);
        className = classNameD[0].trim();
        AppData.getInstance().actualPose = className;
        Log.e("RESULT", AppData.getInstance().actualPose);
      try{
        if(!AppData.getInstance().actualPose.equals(AppData.getInstance().previousPose)){
          switch (AppData.getInstance().actualPose){
            case "alert":
              sendMqttMsg(AppData.getInstance().alarmLed,"on");
              sendMqttMsg(AppData.getInstance().alarmBuzz,"on");
              //TODO UNCOMMENT
              //sendEmail("nunocas3iro@gmail.com","ALERT", "PLEASE HELP");

              msgToButler("ENVIEI ALERTA PARA AS AUTORIDADES");
              break;
            case "cold":
              sendMqttMsg(AppData.getInstance().led1,"on");
              msgToButler("Acabei de ligar o aquecedor");
              break;
            case "hot":
              sendMqttMsg(AppData.getInstance().led2,"on");
              msgToButler("Acabei de ligar a ventoinha");
              break;
            case "goal":
              msgToButler("GOLO GOLO GOLO GOLO PORTUGAL");
              break;
            //case "chicken":
              //msgToButler("GALINHA");
              //break;
            default:
              sendMqttMsg(AppData.getInstance().alarmLed,"off");
              sendMqttMsg(AppData.getInstance().alarmBuzz,"off");
              sendMqttMsg(AppData.getInstance().led1,"off");
              sendMqttMsg(AppData.getInstance().led2,"off");
              break;
          }
        }
        AppData.getInstance().previousPose = className;
      }catch (Exception e){
        Log.e("ERROR MQTT", e.getMessage());
      }


    }
  }

  private void sendEmail(String receiverEmail, String subject, String body) throws Exception {

    new Thread(){
      public void run(){
        GMailSender sender = new GMailSender();
        try {
          sender.sendMail(subject, body, null, receiverEmail);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

    }.start();
  }

  private void msgToButler(String string)  {
    new Thread(){
      public void run() {
        try {
          OkHttpClient client = new OkHttpClient().newBuilder()
                  .build();
          MediaType mediaType = MediaType.parse("application/vnd.onem2mres+json; ty=4");
          RequestBody body = RequestBody.create(mediaType, "{ \"m2m:cin\": {\"rn\": \"sentence" + Random(20).toString() + "\",\"cnf\":\"text/plain:0\",\"con\": \"" + string + "\"} }\n");
          Request request = new Request.Builder()
                  .url("http://192.168.1.78:7579/onem2m/butler/speakcnt")
                  .method("POST", body)
                  .addHeader("Content-Type", "application/vnd.onem2mres+json; ty=4")
                  .addHeader("X-M2M-RI", "0006")
                  .addHeader("Authorization", "Basic c3VwZXJhZG1pbjpzbWFydGhvbWUyMQ==")
                  .build();
          Response response = client.newCall(request).execute();
        } catch (Exception e) {
          Log.e("BUTLER MSG ERROR", e.getMessage());
        }
      }
    }.start();

  }

  private void sendMqttMsg(int id, String status) throws JSONException, MqttException {
    JSONObject jsn = new JSONObject("{to:'" + 841 + "','from':'android', 'action':'turn', 'value':'" + status + "'}");
    MqttMessage send = new MqttMessage();
    send.setPayload(jsn.toString().getBytes());
    AppData.getInstance().mqttClient.publish("/" + id, send);

  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.e(TAG, "Pose detection failed!", e);
  }
}
