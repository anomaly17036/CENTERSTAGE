package org.firstinspires.ftc.teamcode.opmodes;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.common.Robot;

@Config
@TeleOp
public class StandardTeleOp extends LinearOpMode {

    public double LOW_SPEED = 0.375;
    public double MEDIUM_SPEED = 0.7;
    public double HIGH_SPEED = 1.0;
    public double ROTATION_WEIGHT = 0.5;

    public static double INTAKE_UP = 0.2;
    public static double INTAKE_STACK_UP = 0.5;
    public static double INTAKE_HANG_UP = 0.1;
    public static double INTAKE_DOWN = 0.425;
    public static double INTAKE_POWER = 0.6;

    public static double DRFB_UP_REDUCTION = 1.0;
    public static double DRFB_DOWN_REDUCTION = 0.01;
    public static double DRFB_GRAVITY = 0.1;

    enum ArmStates {
        RETRACTED_STATE,
        RETRACTED_LOWERED_STATE,
        SCORING_STATE,
    };

    ArmStates armState;

    Gamepad previousDriver;
    Gamepad previousOperator;
    Gamepad driver;
    Gamepad operator;

    ElapsedTime stateTime;
    ElapsedTime secondStateTime;
    ElapsedTime loopTime;

    Robot robot;

    boolean inverseDrive = false;

    @Override
    public void runOpMode() {

        robot = new Robot();
        robot.init(hardwareMap, true);
        robot.setClawClosed();
        robot.setLauncher();

        previousDriver = new Gamepad();
        previousOperator = new Gamepad();
        driver = new Gamepad();
        operator = new Gamepad();

        armState = ArmStates.RETRACTED_STATE;

        stateTime = new ElapsedTime();
        secondStateTime = new ElapsedTime();
        loopTime = new ElapsedTime();

        waitForStart();

        stateTime.reset();
        secondStateTime.reset();

        while (opModeIsActive()) {
            loopTime.reset();

            driverControl();
            operatorControl();

            routineTasks();
            updateTelemetry();
        }
    }

    private void driverControl() {
        double speed = MEDIUM_SPEED;
        double change = driver.right_trigger - driver.left_trigger;

        speed += change * ((change > 0) ? HIGH_SPEED - MEDIUM_SPEED : MEDIUM_SPEED - LOW_SPEED);

        double r = driver.right_stick_x;
        double y = (r != 0) ? -driver.left_stick_y * (1 - ROTATION_WEIGHT) : -driver.left_stick_y;
        double x = (r != 0) ? driver.left_stick_x * (1 - ROTATION_WEIGHT) : driver.left_stick_x;

//        x = x * Math.cos(heading) - y * Math.sin(heading);
//        y = x * Math.sin(heading) + y * Math.cos(heading);

        double denominator = Math.max(Math.abs(y) + Math.abs(x) + Math.abs(r), 1);

        if (driver.right_bumper) {
            inverseDrive = true;
        } else {
            inverseDrive = false;
        }

        if (inverseDrive) {
            x = -x;
            y = -y;
        }

        robot.setDrivePowers(
            speed * ((y + x + r) / denominator),
            speed * ((y - x + r) / denominator),
            speed * ((y + x - r) / denominator),
            speed * ((y - x - r) / denominator)
        );

        if (driver.a && !previousDriver.a) {
            robot.setCageDown();
            robot.setIntakeAngle(INTAKE_UP);
        }

        if (driver.b && !previousDriver.b) {
            robot.setCageUp();
        }

        if (driver.dpad_up && driver.left_bumper) {
            robot.shootLauncher();
        }
    }

    private void operatorControl() {
        if (operator.share) {
            DRFB_DOWN_REDUCTION = 1.0;
            robot.setIntakeAngle(INTAKE_HANG_UP);
        }

        if (operator.x && !previousOperator.x) {
            robot.setClawOpen();
        }

        if (operator.dpad_left && !previousOperator.dpad_left) {
            robot.setClawClosed();
        }

        intakeControl();
        armControl();
        DRFBControl();
    }

    private void routineTasks() {
        previousDriver.copy(driver);
        previousOperator.copy(operator);
        driver.copy(gamepad1);
        operator.copy(gamepad2);
    }

    private void updateTelemetry() {
        telemetry.addData("DR4B Position", robot.getDRFBPosition());
        telemetry.addData("Arm State", armState.toString());
        telemetry.addData("Loop Time", Math.round(loopTime.time() * 1000));

        telemetry.update();
    }

    private void intakeControl() {
        if (operator.dpad_up) {
            robot.setIntakeAngle(INTAKE_UP);
        } else if (operator.dpad_down) {
            robot.setIntakeAngle(INTAKE_DOWN);
        } else if (operator.dpad_left) {
            robot.setIntakeAngle(INTAKE_STACK_UP);
        }

        if (operator.right_trigger > 0.1) {
            robot.powerIntake(-INTAKE_POWER);
        } else if (operator.left_trigger > 0.1) {
            robot.powerIntake(INTAKE_POWER);
        } else {
            robot.powerIntake(0);
        }
    }

    private void armControl() {
        switch (armState) {
            case RETRACTED_STATE:
                if (stateTime.time() < 0.2) {
                    break;
                }

                robot.setRetracted();

                if (operator.b && !previousOperator.b) {
                    armState = ArmStates.SCORING_STATE;

                    robot.setRetractedUp();

                    stateTime.reset();
                }

                if (operator.a && !previousOperator.a) {
                    armState = ArmStates.RETRACTED_LOWERED_STATE;
                    robot.setClawOpen();
                    robot.setIntakeAngle(INTAKE_UP);

                    secondStateTime.reset();
                    stateTime.reset();
                }

                break;
            case RETRACTED_LOWERED_STATE:
                if (secondStateTime.time() < 0.25) {
                    stateTime.reset();
                    break;
                }

                robot.setRetractedLowered();

                if (stateTime.time() > 0.4) {
                    robot.setClawClosed();
                    stateTime.reset();
                    armState = ArmStates.RETRACTED_STATE;
                }

                break;
            case SCORING_STATE:
                if (stateTime.time() < 0.4) {
                    break;
                }

                robot.setScoring();

                if (operator.a && !previousOperator.a) {
                    armState = ArmStates.RETRACTED_STATE;

                    robot.setScoringIn();

                    stateTime.reset();
                }

                if (driver.dpad_down && !previousDriver.dpad_down) {
                    robot.setClawScoreOpen();
                }

                break;
        }
    }

    private void DRFBControl() {
        double power = -operator.left_stick_y;

        if (robot.getDRFBPosition() > 1200 && power > 0) {
            power = 0;
        } else if (robot.getDRFBPosition() < -10 && power < 0 && !operator.left_bumper) {
            power = 0;
        }

        if (power > 0) {
            robot.powerDRFB(power * DRFB_UP_REDUCTION);
        } else if (power < 0) {
            robot.powerDRFB(power * DRFB_DOWN_REDUCTION);
        } else {
            if (robot.getDRFBPosition() < 100) {
                robot.powerDRFB(0);
            } else {
                robot.powerDRFB(DRFB_GRAVITY);
            }
        }
    }
}
