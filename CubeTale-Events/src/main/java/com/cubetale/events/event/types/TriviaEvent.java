package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class TriviaEvent extends GameEvent {

    private record Question(String question, List<String> answers, List<String> options, String correctOption) {}

    private final List<Question> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int questionTaskId = -1;
    private int questionTimer = 0;
    private boolean awaitingAnswer = false;
    private final Set<UUID> answeredThisRound = new HashSet<>();

    public TriviaEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.TRIVIA);
        loadQuestions();
    }

    private void loadQuestions() {
        File f = new File(plugin.getDataFolder(), "trivia-questions.yml");
        if (!f.exists()) return;
        FileConfiguration yc = YamlConfiguration.loadConfiguration(f);
        List<?> list = yc.getList("questions");
        if (list == null) return;
        for (Object obj : list) {
            if (!(obj instanceof Map<?,?> map)) continue;
            String q = (String) map.get("question");
            Object rawAns  = map.get("answers");
            Object rawOpts = map.get("options");
            Object rawCo   = map.get("correct-option");
            @SuppressWarnings("unchecked")
            List<String> ans  = rawAns  instanceof List<?> l1 ? (List<String>) l1 : new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> opts = rawOpts instanceof List<?> l2 ? (List<String>) l2 : new ArrayList<>();
            String co = rawCo instanceof String s ? s : "";
            if (q != null && !ans.isEmpty()) questions.add(new Question(q, ans, opts, co));
        }
        Collections.shuffle(questions);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        FileConfiguration cfg = plugin.getEventsConfig().get();
        int questionsPerGame = cfg.getInt("events.trivia.questions-per-game", 8);
        if (questions.size() > questionsPerGame) questions.subList(questionsPerGame, questions.size()).clear();

        askNextQuestion();
    }

    private void askNextQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            plugin.getEventManager().endCurrentEvent(false);
            return;
        }

        Question q = questions.get(currentQuestionIndex++);
        awaitingAnswer = true;
        answeredThisRound.clear();

        int questionTime = plugin.getEventsConfig().get().getInt("events.trivia.question-time", 20);

        Bukkit.broadcastMessage(plugin.msg().get("trivia-question", Map.of("question", q.question())));
        if (!q.options().isEmpty()) {
            for (String opt : q.options()) {
                Bukkit.broadcastMessage(MessageUtil.colorize("  &7" + opt));
            }
        }

        questionTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            awaitingAnswer = false;
            String answer = q.answers().isEmpty() ? "?" : q.answers().get(0);
            Bukkit.broadcastMessage(plugin.msg().get("trivia-timeout", Map.of("answer", answer)));
            askNextQuestion();
        }, questionTime * 20L);
    }

    /**
     * Called from EventListener when a chat message is sent during a running Trivia event.
     * args[0] = Player, args[1] = String message
     */
    @Override
    public void onPlayerAction(Player player, Object... args) {
        if (!awaitingAnswer) return;
        if (!isParticipant(player)) return;
        if (answeredThisRound.contains(player.getUniqueId())) return;
        if (args.length < 1 || !(args[0] instanceof String message)) return;

        Question q = questions.get(currentQuestionIndex - 1);
        boolean correct = q.answers().stream()
                .anyMatch(a -> a.equalsIgnoreCase(message.trim()))
                || (!q.correctOption().isEmpty() && q.correctOption().equalsIgnoreCase(message.trim()));

        if (correct) {
            answeredThisRound.add(player.getUniqueId());
            int pts = plugin.getEventsConfig().get().getInt("events.trivia.points-per-correct", 1);
            int bonus = plugin.getEventsConfig().get().getInt("events.trivia.bonus-speed-points", 1);
            int awarded = answeredThisRound.size() == 1 ? pts + bonus : pts;
            addScore(player, awarded);

            Bukkit.broadcastMessage(plugin.msg().get("trivia-correct", Map.of(
                "player", player.getName(),
                "points", String.valueOf(awarded)
            )));

            // Cancel timer, move to next question
            if (questionTaskId != -1) { Bukkit.getScheduler().cancelTask(questionTaskId); questionTaskId = -1; }
            awaitingAnswer = false;
            Bukkit.getScheduler().runTaskLater(plugin, this::askNextQuestion, 40L);
        }
    }

    @Override
    public void end(boolean forced) {
        if (questionTaskId != -1) { Bukkit.getScheduler().cancelTask(questionTaskId); questionTaskId = -1; }
        cancelTask();
    }
}
