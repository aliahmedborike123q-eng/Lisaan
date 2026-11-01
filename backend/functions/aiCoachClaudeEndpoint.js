/**
 * Firebase Cloud Function / Express Endpoint: POST /v1/ai-coach/chat
 * يستقبل رسالة المتعلم من تطبيق "لِسان" ويستدعي Claude 3.5 Sonnet
 * لإرجاع رد حواري طبيعي + تصحيح القواعد والمفردات مع شرح مبسط بالعربية.
 */
const Anthropic = require("@anthropic-ai/sdk");

const anthropic = new Anthropic({
  apiKey: process.env.ANTHROPIC_API_KEY,
});

exports.aiCoachChatHandler = async (req, res) => {
  try {
    const { scenarioId, learnerLevel, userMessage, history = [] } = req.body;

    const systemPrompt = `You are "Nabeeh (نبيه)", the friendly, encouraging AI Language Coach inside the "Lisaan (لِسان)" app helping Arabic speakers master English.
Current Roleplay Scenario: ${scenarioId}
Learner CEFR Level: ${learnerLevel}

Respond strictly in valid JSON with the following schema:
{
  "replyEn": "Your natural conversational reply in English tailored to ${learnerLevel}",
  "replyTranslationAr": "Accurate Arabic translation of your reply",
  "correction": {
    "hasMistake": true or false,
    "originalText": "The learner's original message",
    "correctedText": "Natural corrected English version (or same if perfect)",
    "explanationAr": "Brief, warm, simple Arabic explanation of the grammar/vocabulary fix",
    "explanationEn": "Brief English explanation"
  },
  "suggestedFollowUpEn": "A short hint sentence the learner can say next"
}`;

    const messages = [
      ...history.map((h) => ({ role: h.role, content: h.content })),
      { role: "user", content: userMessage },
    ];

    const response = await anthropic.messages.create({
      model: "claude-3-5-sonnet-latest",
      max_tokens: 600,
      temperature: 0.4,
      system: systemPrompt,
      messages: messages,
    });

    const rawText = response.content[0]?.text || "{}";
    const parsed = JSON.parse(rawText);
    return res.status(200).json(parsed);
  } catch (error) {
    console.error("Claude AI Coach Error:", error);
    return res.status(500).json({ error: "Failed to process AI Coach response" });
  }
};
