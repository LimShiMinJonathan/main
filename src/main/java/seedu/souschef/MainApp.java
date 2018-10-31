package seedu.souschef;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

import com.google.common.eventbus.Subscribe;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import seedu.souschef.commons.core.Config;
import seedu.souschef.commons.core.EventsCenter;
import seedu.souschef.commons.core.LogsCenter;
import seedu.souschef.commons.core.Version;
import seedu.souschef.commons.events.ui.ExitAppRequestEvent;
import seedu.souschef.commons.exceptions.DataConversionException;
import seedu.souschef.commons.util.ConfigUtil;
import seedu.souschef.commons.util.StringUtil;
import seedu.souschef.logic.Logic;
import seedu.souschef.logic.LogicManager;
import seedu.souschef.model.AppContent;
import seedu.souschef.model.ModelSet;
import seedu.souschef.model.ModelSetCoordinator;
import seedu.souschef.model.ReadOnlyAppContent;
import seedu.souschef.model.UserPrefs;
import seedu.souschef.model.util.SampleDataUtil;
import seedu.souschef.storage.JsonUserPrefsStorage;
import seedu.souschef.storage.Storage;
import seedu.souschef.storage.StorageManager;
import seedu.souschef.storage.UserPrefsStorage;
import seedu.souschef.ui.Ui;
import seedu.souschef.ui.UiManager;


/**
 * The main entry point to the application.
 */
public class MainApp extends Application {

    public static final Version VERSION = new Version(1, 3, 0, true);

    private static final Logger logger = LogsCenter.getLogger(MainApp.class);

    protected Ui ui;
    protected Logic logic;
    protected Storage storage;
    protected ModelSet modelSet;
    protected Config config;
    protected UserPrefs userPrefs;


    @Override
    public void init() throws Exception {
        logger.info("=============================[ Initializing AppContent ]===========================");
        super.init();

        AppParameters appParameters = AppParameters.parse(getParameters());
        config = initConfig(appParameters.getConfigPath());

        UserPrefsStorage userPrefsStorage = new JsonUserPrefsStorage(config.getUserPrefsFilePath());
        userPrefs = initPrefs(userPrefsStorage);
        AppContent appContent = new AppContent();

        //storage = new StorageManager(recipeStorage, userPrefsStorage);
        storage = new StorageManager(userPrefsStorage, userPrefs, appContent);

        initLogging(config);

        //model segment
        modelSet = initModelManager(storage, userPrefs);


        logic = new LogicManager(modelSet, storage);

        //ui
        ui = new UiManager(logic, config, userPrefs);


        logic.setUi(ui);


        initEventsCenter();
    }

    /**
     * Returns a {@code ModelManager} with the data from {@code storage}'s address book and {@code userPrefs}. <br>
     * The data from the sample address book will be used instead if {@code storage}'s address book is not found,
     * or an empty address book will be used instead if errors occur when reading {@code storage}'s address book.
     */
    private ModelSet initModelManager(Storage storage, UserPrefs userPrefs) {
        Optional<ReadOnlyAppContent> readOnlyAppContentOptional;
        ReadOnlyAppContent initialData;
        try {

            readOnlyAppContentOptional = storage.readAll();

            if (!readOnlyAppContentOptional.isPresent()) {
                logger.info("Data file not found. Will be starting with a sample AppContent");
            }
            initialData = readOnlyAppContentOptional.orElseGet(SampleDataUtil::getSampleAddressBook);

        } catch (DataConversionException e) {
            logger.warning("Data file not in the correct format. Will be starting with an empty AppContent");
            initialData = new AppContent();
        } catch (IOException e) {
            logger.warning("Problem while reading from the file. Will be starting with an empty AppContent");
            initialData = new AppContent();
        }

        return new ModelSetCoordinator(initialData, userPrefs);
    }

    private void initLogging(Config config) {
        LogsCenter.init(config);
    }

    /**
     * Returns a {@code Config} using the file at {@code configFilePath}. <br>
     * The default file path {@code Config#DEFAULT_CONFIG_FILE} will be used instead
     * if {@code configFilePath} is null.
     */
    protected Config initConfig(Path configFilePath) {
        Config initializedConfig;
        Path configFilePathUsed;

        configFilePathUsed = Config.DEFAULT_CONFIG_FILE;

        if (configFilePath != null) {
            logger.info("Custom Config file specified " + configFilePath);
            configFilePathUsed = configFilePath;
        }

        logger.info("Using config file : " + configFilePathUsed);

        try {
            Optional<Config> configOptional = ConfigUtil.readConfig(configFilePathUsed);
            initializedConfig = configOptional.orElse(new Config());
        } catch (DataConversionException e) {
            logger.warning("Config file at " + configFilePathUsed + " is not in the correct format. "
                    + "Using default config properties");
            initializedConfig = new Config();
        }

        //Update config file in case it was missing to begin with or there are new/unused fields
        try {
            ConfigUtil.saveConfig(initializedConfig, configFilePathUsed);
        } catch (IOException e) {
            logger.warning("Failed to save config file : " + StringUtil.getDetails(e));
        }
        return initializedConfig;
    }

    /**
     * Returns a {@code UserPrefs} using the file at {@code storage}'s user prefs file path,
     * or a new {@code UserPrefs} with default configuration if errors occur when
     * reading from the file.
     */
    protected UserPrefs initPrefs(UserPrefsStorage storage) {
        Path prefsFilePath = storage.getUserPrefsFilePath();
        logger.info("Using prefs file : " + prefsFilePath);

        UserPrefs initializedPrefs;
        try {
            Optional<UserPrefs> prefsOptional = storage.readUserPrefs();
            initializedPrefs = prefsOptional.orElse(new UserPrefs());
        } catch (DataConversionException e) {
            logger.warning("UserPrefs file at " + prefsFilePath + " is not in the correct format. "
                    + "Using default user prefs");
            initializedPrefs = new UserPrefs();
        } catch (IOException e) {
            logger.warning("Problem while reading from the file. Will be starting with an empty AppContent");
            initializedPrefs = new UserPrefs();
        }

        //Update prefs file in case it was missing to begin with or there are new/unused fields
        try {
            storage.saveUserPrefs(initializedPrefs);
        } catch (IOException e) {
            logger.warning("Failed to saveAppContent config file : " + StringUtil.getDetails(e));
        }

        return initializedPrefs;
    }

    private void initEventsCenter() {
        EventsCenter.getInstance().registerHandler(this);
    }

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting AppContent " + MainApp.VERSION);
        ui.start(primaryStage);
    }

    @Override
    public void stop() {
        logger.info("============================ [ Stopping Address Book ] =============================");
        ui.stop();
        try {
            storage.saveUserPrefs(userPrefs);
        } catch (IOException e) {
            logger.severe("Failed to saveAppContent preferences " + StringUtil.getDetails(e));
        }
        Platform.exit();
        System.exit(0);
    }

    @Subscribe
    public void handleExitAppRequestEvent(ExitAppRequestEvent event) {
        logger.info(LogsCenter.getEventHandlingLogMessage(event));
        stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}