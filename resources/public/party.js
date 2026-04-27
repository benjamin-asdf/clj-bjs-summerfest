// Party Games — ball-passing between connected phones
// Modes: bump (accelerometer pairing), room (manual chain), radar (compass ordering)
// Single global room per mode — no room codes needed.

(function () {
  "use strict";

  const MODE = document.body.dataset.partyMode || "room";
  // Fixed room name per mode — everyone at the party shares the same room
  const ROOM = "summerfest-" + MODE;

  // --- State ---
  let ws = null;
  let myId = null;
  let players = {};
  let chain = [];
  let ballHolder = null;
  let joined = false;

  // Ball
  const ball = {
    x: 0, y: 0,
    vx: 3.5, vy: 2,
    radius: 18,
    active: false,
    trail: [],
    flash: 0,
  };

  const COLORS = ["#a1fe9a", "#F689FF", "#ffa500", "#00d4ff", "#ff6b6b"];
  let ballColor = COLORS[0];

  // --- DOM refs ---
  const canvas = document.getElementById("party-canvas");
  const ctx = canvas.getContext("2d");
  const lobby = document.getElementById("party-lobby");
  const gameUI = document.getElementById("party-game");
  const chainEl = document.getElementById("party-chain");
  const playerCountEl = document.getElementById("player-count");

  // --- Canvas sizing ---
  let W = 0, H = 0;

  function resize() {
    const dpr = window.devicePixelRatio || 1;
    W = canvas.clientWidth;
    H = canvas.clientHeight;
    canvas.width = W * dpr;
    canvas.height = H * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  window.addEventListener("resize", resize);

  // --- WebSocket ---
  function connect() {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    const base = window.SUMMERFEST_BASE || "";
    const url = `${proto}//${location.host}${base}/party/ws?room=${encodeURIComponent(ROOM)}&mode=${MODE}`;
    ws = new WebSocket(url);

    ws.onopen = () => console.log("Party WS connected");

    ws.onmessage = (e) => {
      handleMessage(JSON.parse(e.data));
    };

    ws.onclose = () => {
      console.log("Party WS closed");
      if (joined) setTimeout(connect, 2000);
    };
  }

  function send(msg) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg));
    }
  }

  // --- Message handling ---
  function handleMessage(msg) {
    switch (msg.type) {
      case "welcome":
        myId = msg.playerId;
        break;

      case "room-state":
        players = msg.players || {};
        chain = msg.chain || [];
        ballHolder = msg.ballHolder;
        updateUI();
        if (ballHolder === myId && !ball.active) {
          spawnBall();
        } else if (ballHolder !== myId) {
          ball.active = false;
        }
        break;

      case "ball-enter":
        receiveBall(msg);
        break;

      case "ball-holder":
        ballHolder = msg.holderId;
        if (ballHolder !== myId) ball.active = false;
        updateUI();
        break;

      case "bump-success":
        if (navigator.vibrate) navigator.vibrate([100, 50, 100]);
        showToast("Bump! Connected!");
        break;

      case "compass-update":
        chain = msg.chain || chain;
        if (msg.playerId && players[msg.playerId]) {
          players[msg.playerId].compass = msg.compass;
        }
        updateUI();
        break;
    }
  }

  // --- Ball physics ---
  function spawnBall() {
    ball.x = W / 2;
    ball.y = H / 2;
    ball.vx = (Math.random() > 0.5 ? 1 : -1) * (2.5 + Math.random() * 2);
    ball.vy = (Math.random() - 0.5) * 4;
    ball.active = true;
    ball.trail = [];
    ball.flash = 0;
    ballColor = COLORS[Math.floor(Math.random() * COLORS.length)];
  }

  function receiveBall(msg) {
    const fromEdge = msg.fromEdge;
    ball.x = fromEdge === "left" ? ball.radius + 2 : W - ball.radius - 2;
    ball.y = msg.y * H;
    ball.vx = Math.abs(msg.vx || 3.5) * (fromEdge === "left" ? 1 : -1);
    ball.vy = msg.vy || 0;
    ball.active = true;
    ball.trail = [];
    ball.flash = 1.0;
    ballHolder = myId;
    if (navigator.vibrate) navigator.vibrate(50);
  }

  function updateBall(dt) {
    if (!ball.active) return;

    ball.x += ball.vx * dt * 60;
    ball.y += ball.vy * dt * 60;

    // Bounce top/bottom
    if (ball.y - ball.radius < 0) {
      ball.y = ball.radius;
      ball.vy = Math.abs(ball.vy);
    }
    if (ball.y + ball.radius > H) {
      ball.y = H - ball.radius;
      ball.vy = -Math.abs(ball.vy);
    }

    // Exit left/right → transfer to next phone
    if (ball.x + ball.radius < 0) {
      ball.active = false;
      send({ type: "ball-exit", edge: "left", y: ball.y / H, vx: ball.vx, vy: ball.vy });
    } else if (ball.x - ball.radius > W) {
      ball.active = false;
      send({ type: "ball-exit", edge: "right", y: ball.y / H, vx: ball.vx, vy: ball.vy });
    }

    // Trail
    ball.trail.push({ x: ball.x, y: ball.y });
    if (ball.trail.length > 20) ball.trail.shift();

    // Flash decay
    if (ball.flash > 0) ball.flash -= dt * 3;
  }

  // --- Rendering ---
  function drawBall() {
    if (!ball.active && ballHolder !== myId) {
      ctx.save();
      ctx.globalAlpha = 0.3 + 0.1 * Math.sin(Date.now() / 500);
      ctx.fillStyle = "#666";
      ctx.font = "16px system-ui";
      ctx.textAlign = "center";
      const myIdx = chain.indexOf(myId);
      const holderIdx = chain.indexOf(ballHolder);
      let arrow = "";
      if (myIdx >= 0 && holderIdx >= 0) {
        arrow = holderIdx < myIdx ? " <<<" : " >>>";
      }
      ctx.fillText("Ball is elsewhere" + arrow, W / 2, H / 2);
      ctx.restore();
      return;
    }

    if (!ball.active) return;

    // Trail
    for (let i = 0; i < ball.trail.length; i++) {
      const t = ball.trail[i];
      const alpha = (i / ball.trail.length) * 0.4;
      const r = ball.radius * (i / ball.trail.length) * 0.7;
      ctx.beginPath();
      ctx.arc(t.x, t.y, r, 0, Math.PI * 2);
      ctx.fillStyle = ballColor;
      ctx.globalAlpha = alpha;
      ctx.fill();
    }
    ctx.globalAlpha = 1;

    // Glow
    const glow = ctx.createRadialGradient(
      ball.x, ball.y, ball.radius * 0.3,
      ball.x, ball.y, ball.radius * 2.5
    );
    glow.addColorStop(0, ballColor + "80");
    glow.addColorStop(1, ballColor + "00");
    ctx.beginPath();
    ctx.arc(ball.x, ball.y, ball.radius * 2.5, 0, Math.PI * 2);
    ctx.fillStyle = glow;
    ctx.fill();

    // Ball body
    ctx.beginPath();
    ctx.arc(ball.x, ball.y, ball.radius, 0, Math.PI * 2);
    const grad = ctx.createRadialGradient(
      ball.x - ball.radius * 0.3, ball.y - ball.radius * 0.3, ball.radius * 0.1,
      ball.x, ball.y, ball.radius
    );
    grad.addColorStop(0, "#fff");
    grad.addColorStop(0.4, ballColor);
    grad.addColorStop(1, ballColor + "88");
    ctx.fillStyle = grad;
    ctx.fill();

    // Entry flash
    if (ball.flash > 0) {
      ctx.save();
      ctx.globalAlpha = ball.flash * 0.5;
      ctx.fillStyle = ballColor;
      ctx.fillRect(0, 0, W, H);
      ctx.restore();
    }
  }

  function drawEdgeIndicators() {
    const myIdx = chain.indexOf(myId);
    if (myIdx < 0 || chain.length < 2) return;

    ctx.save();
    ctx.globalAlpha = 0.15 + 0.05 * Math.sin(Date.now() / 800);
    ctx.font = "bold 14px system-ui";

    // Left neighbor
    const leftIdx = (myIdx - 1 + chain.length) % chain.length;
    const leftPlayer = players[chain[leftIdx]];
    if (leftPlayer) {
      ctx.fillStyle = COLORS[1];
      ctx.textAlign = "left";
      ctx.fillText(leftPlayer.name, 8, H / 2);
      ctx.beginPath();
      ctx.moveTo(4, H / 2 - 20);
      ctx.lineTo(0, H / 2 - 10);
      ctx.lineTo(4, H / 2);
      ctx.strokeStyle = COLORS[1];
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    // Right neighbor
    const rightIdx = (myIdx + 1) % chain.length;
    const rightPlayer = players[chain[rightIdx]];
    if (rightPlayer) {
      ctx.fillStyle = COLORS[2];
      ctx.textAlign = "right";
      ctx.fillText(rightPlayer.name, W - 8, H / 2);
      ctx.beginPath();
      ctx.moveTo(W - 4, H / 2 - 20);
      ctx.lineTo(W, H / 2 - 10);
      ctx.lineTo(W - 4, H / 2);
      ctx.strokeStyle = COLORS[2];
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    ctx.restore();
  }

  function drawMyPosition() {
    const myIdx = chain.indexOf(myId);
    if (myIdx < 0) return;

    ctx.save();
    ctx.globalAlpha = 0.4;
    ctx.fillStyle = "#fff";
    ctx.font = "12px system-ui";
    ctx.textAlign = "center";
    ctx.fillText(`${myIdx + 1} / ${chain.length}`, W / 2, 20);
    ctx.restore();
  }

  // --- Game loop ---
  let lastTime = 0;

  function gameLoop(time) {
    const dt = Math.min((time - lastTime) / 1000, 0.05);
    lastTime = time;

    ctx.clearRect(0, 0, W, H);

    if (joined) {
      updateBall(dt);
      drawEdgeIndicators();
      drawBall();
      drawMyPosition();
    }

    requestAnimationFrame(gameLoop);
  }

  // --- UI updates ---
  function updateUI() {
    const count = Object.keys(players).length;
    if (playerCountEl) playerCountEl.textContent = count;

    if (chainEl) {
      chainEl.innerHTML = "";
      chain.forEach((pid, i) => {
        const p = players[pid];
        if (!p) return;
        const el = document.createElement("span");
        el.className = "chain-player" + (pid === myId ? " me" : "") + (pid === ballHolder ? " has-ball" : "");
        el.textContent = p.name || "?";
        if (MODE === "room" && pid !== myId) {
          el.draggable = true;
          el.dataset.pid = pid;
        }
        chainEl.appendChild(el);
        if (i < chain.length - 1) {
          const arrow = document.createElement("span");
          arrow.className = "chain-arrow";
          arrow.textContent = " \u2192 ";
          chainEl.appendChild(arrow);
        }
      });
    }
  }

  // --- Toast notification ---
  function showToast(text) {
    const toast = document.createElement("div");
    toast.className = "party-toast";
    toast.textContent = text;
    document.body.appendChild(toast);
    setTimeout(() => toast.classList.add("show"), 10);
    setTimeout(() => {
      toast.classList.remove("show");
      setTimeout(() => toast.remove(), 300);
    }, 2000);
  }

  // --- Bump detection (accelerometer) ---
  let lastAccel = { x: 0, y: 0, z: 0 };
  let lastBumpTime = 0;

  function initBumpDetection() {
    if (MODE !== "bump") return;

    window.addEventListener("devicemotion", (e) => {
      const a = e.accelerationIncludingGravity;
      if (!a) return;
      const dx = a.x - lastAccel.x;
      const dy = a.y - lastAccel.y;
      const dz = a.z - lastAccel.z;
      const magnitude = Math.sqrt(dx * dx + dy * dy + dz * dz);
      lastAccel = { x: a.x, y: a.y, z: a.z };

      if (magnitude > 15 && Date.now() - lastBumpTime > 1000) {
        lastBumpTime = Date.now();
        send({ type: "bump", timestamp: Date.now() });
        if (navigator.vibrate) navigator.vibrate(100);
        showToast("Bump detected!");
      }
    });
  }

  // --- Compass (DeviceOrientation) ---
  function initCompass() {
    if (MODE !== "radar") return;

    window.addEventListener("deviceorientationabsolute", handleOrientation);
    window.addEventListener("deviceorientation", handleOrientation);
  }

  function handleOrientation(e) {
    let heading = e.alpha;
    if (e.webkitCompassHeading !== undefined) {
      heading = e.webkitCompassHeading;
    } else if (heading !== null) {
      heading = (360 - heading) % 360;
    }
    if (heading === null || heading === undefined) return;

    send({ type: "sensor", compass: Math.round(heading) });
  }

  // --- Drag reorder for room mode ---
  let draggedPid = null;

  function initDragReorder() {
    if (MODE !== "room" || !chainEl) return;

    chainEl.addEventListener("dragstart", (e) => {
      draggedPid = e.target.dataset.pid;
    });

    chainEl.addEventListener("dragover", (e) => e.preventDefault());

    chainEl.addEventListener("drop", (e) => {
      e.preventDefault();
      const targetPid = e.target.dataset.pid || e.target.closest("[data-pid]")?.dataset.pid;
      if (draggedPid && targetPid && draggedPid !== targetPid) {
        const newChain = [...chain];
        const fromIdx = newChain.indexOf(draggedPid);
        const toIdx = newChain.indexOf(targetPid);
        newChain.splice(fromIdx, 1);
        newChain.splice(toIdx, 0, draggedPid);
        send({ type: "reorder", chain: newChain });
      }
      draggedPid = null;
    });
  }

  // --- Touch-based reorder (for mobile) ---
  function initTouchReorder() {
    if (MODE !== "room" || !chainEl) return;

    chainEl.addEventListener("click", (e) => {
      const pid = e.target.dataset?.pid;
      if (!pid || pid === myId) return;
      const newChain = [...chain];
      const idx = newChain.indexOf(pid);
      if (idx > 0) {
        [newChain[idx - 1], newChain[idx]] = [newChain[idx], newChain[idx - 1]];
        send({ type: "reorder", chain: newChain });
      }
    });
  }

  // --- Permission request for sensors (iOS 13+) ---
  async function requestSensorPermission() {
    if (typeof DeviceMotionEvent !== "undefined" && typeof DeviceMotionEvent.requestPermission === "function") {
      try {
        const perm = await DeviceMotionEvent.requestPermission();
        if (perm !== "granted") showToast("Motion sensor access denied");
      } catch (e) {
        console.warn("Motion permission error:", e);
      }
    }
    if (typeof DeviceOrientationEvent !== "undefined" && typeof DeviceOrientationEvent.requestPermission === "function") {
      try {
        const perm = await DeviceOrientationEvent.requestPermission();
        if (perm !== "granted") showToast("Orientation sensor access denied");
      } catch (e) {
        console.warn("Orientation permission error:", e);
      }
    }
  }

  // --- Join logic (single room, just need a name) ---
  window.partyJoin = async function () {
    const nameInput = document.getElementById("player-name");
    const name = (nameInput?.value || "").trim() || "Guest";

    await requestSensorPermission();
    connect();

    const checkWs = setInterval(() => {
      if (myId) {
        clearInterval(checkWs);
        send({ type: "join", name: name });
        joined = true;
        lobby.style.display = "none";
        gameUI.style.display = "flex";
        resize();

        initBumpDetection();
        initCompass();
        initDragReorder();
        initTouchReorder();

        requestAnimationFrame(gameLoop);
      }
    }, 100);
  };

  // --- Touch to launch ball (if you hold it but it's not active) ---
  document.addEventListener("click", () => {
    if (joined && ballHolder === myId && !ball.active) {
      spawnBall();
    }
  });
})();
