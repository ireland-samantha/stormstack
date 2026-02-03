# Stormstack (formerly Lightning Engine)
![Status](https://img.shields.io/badge/status-experimental-blueviolet)
![Java](https://img.shields.io/badge/java-25-blue)
![License](https://img.shields.io/github/license/ireland-samantha/lightning-engine)

## What is this?
StormStack is a Java-based authoritative multiplayer game server platform.
It enables hot-deployment of untrusted game logic at runtime, using JVM ClassLoader isolation and JWT-scoped ECS access to safely run multiple matches on shared infrastructure.

I built StormStack to explore modular and secure backend architecture within a game dev context.

### How It Works

1. **Write game modules** - Implement interfaces and build JAR files containing your backend game logic (ECS components, systems, commands)
3. **Install module in cluster** - Upload modules to the control plane and distribute to engine nodes

```
$ lightning module upload MyGameModule 1.0.0 ./target/my-module.jar
✓ Module MyModule@1.0.0 uploaded successfully
```
```
$ lightning module distribute MyModule 1.0.0
✓ Module MyGameModule@1.0.0 distributed to 3 nodes
```

4. **Deploy matches** 
```
$ lightning deploy --modules EntityModule,RigidBodyModule,RenderingModule

# Output:
✓ Match deployed successfully!
    Match ID:     node-1-42-1
    Node:         node-1
    Container:    42
    Status:       RUNNING
 
  Endpoints:
    HTTP:         http://backend:8080/api/containers/42
    WebSocket:    ws://backend:8080/ws/containers/42/matches/1/snapshots
    Commands:     ws://backend:8080/ws/containers/42/matches/1/commands
```
5. **Stream game state** - Clients receive real-time ECS snapshots via WebSocket.

```
[CMD] lightning snapshot get -o json
[INFO] {
  "matchId": 1,
  "tick": 129,
  "modules": [
    {
      "name": "EntityModule",
      "version": "1.0",
      "components": [
        {
          "name": "ENTITY_ID",
          "values": [
            1,
            2,
            3,... ]
        }
      ]
    }
  ]
}
```

## Want to learn more?
* [Documentation](https://ireland-samantha.github.io/stormstack)
* [Architecture design](docs/legacy/architecture.md)
* [CLI quickstart](docs/legacy/cli-quickstart.md)

## License

MIT — Use this however you want (just not for evil tho)
