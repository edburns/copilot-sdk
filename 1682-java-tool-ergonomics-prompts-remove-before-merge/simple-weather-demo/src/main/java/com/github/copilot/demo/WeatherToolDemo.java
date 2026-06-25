package com.github.copilot.demo;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.github.copilot.AllowCopilotExperimental;
import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolSet;

/**
 * Demo: GitHub Copilot SDK Java Tools API
 *
 * Shows how to:
 * 1. Annotate a tool class with @CopilotTool
 * 2. Register tools via ToolDefinition.fromObject()
 * 3. Send a prompt that triggers tool invocation
 * 4. Observe the LLM invoking the tool and returning results
 */
@AllowCopilotExperimental
public class WeatherToolDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== GitHub Copilot SDK — Java Tools API Demo ===");
        System.out.println();

        List<String> cities = List.of(
                "Pforzheim", "Worms", "Kissing", "Fucking",
                "Titting", "Petting", "Kotten", "Gifhorn",
                "Bünde", "Ulm");
        String city = cities.get(ThreadLocalRandom.current().nextInt(cities.size()));

        // Step 1: Create the tool instance
        WeatherTools weatherTools = new WeatherTools();
        System.out.println("[1] Created WeatherTools instance");

        // Step 2: Generate ToolDefinitions from the annotated object
        //         The SDK's annotation processor generated the companion metadata class
        //         at compile time; fromObject() uses it to build schemas and handlers.
        List<ToolDefinition> toolDefs = ToolDefinition.fromObject(weatherTools);
        System.out.println("[2] Generated " + toolDefs.size() + " tool definition(s) from @CopilotTool annotations:");
        for (ToolDefinition td : toolDefs) {
            System.out.println("    - " + td.name() + ": " + td.description());
        }
        System.out.println();

        // Step 3: Create and start the Copilot client (uses logged-in user's token)
        System.out.println("[3] Starting CopilotClient (using logged-in user credentials)...");
        CopilotClientOptions clientOptions = new CopilotClientOptions()
                .setUseLoggedInUser(true);

        try (CopilotClient client = new CopilotClient(clientOptions)) {
            client.start().get(30, TimeUnit.SECONDS);
            System.out.println("    Connected to Copilot CLI server.");
            System.out.println();

            // Step 4: Create a session with our tools registered
            System.out.println("[4] Creating session with tools registered...");
            SessionConfig sessionConfig = new SessionConfig()
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                    .setAvailableTools(new ToolSet().addCustom("*"))
                    .setTools(toolDefs);

            CopilotSession session = client.createSession(sessionConfig)
                    .get(30, TimeUnit.SECONDS);
            System.out.println("    Session created.");
            System.out.println();

            try {
                // Step 5: Send a prompt that will cause the LLM to invoke our weather tool
                String prompt = "What's the weather like in " + city + "? Use the get_weather tool to find out.";
                System.out.println("[5] Sending prompt: \"" + prompt + "\"");
                System.out.println("    Waiting for LLM to process and invoke tools...");
                System.out.println();

                AssistantMessageEvent response = session.sendAndWait(
                        new MessageOptions().setPrompt(prompt), 60_000)
                        .get(90, TimeUnit.SECONDS);

                // Step 6: Print the final response
                System.out.println("=== LLM RESPONSE ===");
                System.out.println(response.getData().content());
                System.out.println("====================");
                System.out.println();
                System.out.println("[Done] The LLM invoked our @CopilotTool-annotated method and used its result.");
            } finally {
                session.close();
            }
        }
    }
}
