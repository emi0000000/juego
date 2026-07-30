# server.py — Servidor de ÉXODO: LA VENGANZA DE NOX
#
# Guarda el progreso de la partida y lleva la tabla de récords.
# Es OPCIONAL: si no lo enciendes, el juego guarda igual en el archivo local
# exodo_save.properties y funciona sin problemas.
#
# Instalar:  pip install fastapi uvicorn
# Ejecutar:  uvicorn server:app --reload

import json
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="Servidor de ÉXODO", version="2.0")

DATA_FILE = Path("server_data.json")


# ----------------------------------------------------------------------
# Modelos
# ----------------------------------------------------------------------

class PlayerData(BaseModel):
    """Progreso de una partida. Coincide con lo que envía SaveManager.java."""
    level: int = Field(1, ge=1, le=6)
    health: int = Field(100, ge=0, le=100)
    money: int = Field(0, ge=0)
    difficulty: int = Field(1, ge=0, le=2)
    sanity: int = Field(100, ge=0, le=100)
    claws: bool = False
    crossbow: bool = False
    plasma: bool = False
    family: str = "000"        # un carácter por familiar: WILL, NINA, MITSY
    score: int = 0
    play_time_ms: int = 0


class ScoreEntry(BaseModel):
    name: str = Field("NOX", max_length=20)
    score: int = 0
    level: int = Field(1, ge=1, le=6)
    difficulty: int = Field(1, ge=0, le=2)


# ----------------------------------------------------------------------
# Persistencia en disco (para que no se pierda al reiniciar el servidor)
# ----------------------------------------------------------------------

def load_data() -> dict:
    if DATA_FILE.exists():
        try:
            return json.loads(DATA_FILE.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            pass
    return {"players": {}, "scores": []}


def save_data(data: dict) -> None:
    DATA_FILE.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")


DIFFICULTY_NAMES = {0: "FÁCIL", 1: "NORMAL", 2: "PESADILLA"}
FAMILY_NAMES = ["WILL", "NINA", "MITSY"]


# ----------------------------------------------------------------------
# Progreso del jugador
# ----------------------------------------------------------------------

@app.post("/api/save_player/{player_id}")
async def save_player(player_id: str, data: PlayerData):
    store = load_data()
    store["players"][player_id] = data.model_dump()
    save_data(store)
    return {"status": "ok", "message": f"Progreso de {player_id} sincronizado"}


@app.get("/api/load_player/{player_id}")
async def load_player(player_id: str):
    store = load_data()
    player = store["players"].get(player_id)
    if player is None:
        raise HTTPException(status_code=404, detail="Jugador no encontrado")
    return player


@app.delete("/api/player/{player_id}")
async def delete_player(player_id: str):
    store = load_data()
    if store["players"].pop(player_id, None) is None:
        raise HTTPException(status_code=404, detail="Jugador no encontrado")
    save_data(store)
    return {"status": "ok", "message": "Partida borrada"}


@app.get("/api/progress/{player_id}")
async def progress_report(player_id: str):
    """Resumen legible del avance: útil para revisar la partida sin abrir el juego."""
    store = load_data()
    player = store["players"].get(player_id)
    if player is None:
        raise HTTPException(status_code=404, detail="Jugador no encontrado")

    family = player.get("family", "000")
    rescued = [FAMILY_NAMES[i] for i, c in enumerate(family[:3]) if c == "1"]
    weapons = [n for n, k in (("Garras de Acero", "claws"),
                              ("Ballesta", "crossbow"),
                              ("Lanzador de Plasma", "plasma")) if player.get(k)]
    seconds = player.get("play_time_ms", 0) // 1000

    return {
        "barrio": player.get("level", 1),
        "barrios_completados": player.get("level", 1) - 1,
        "dificultad": DIFFICULTY_NAMES.get(player.get("difficulty", 1), "NORMAL"),
        "salud": player.get("health", 100),
        "dinero": player.get("money", 0),
        "cordura": player.get("sanity", 100),
        "familia_rescatada": rescued,
        "familia_pendiente": [n for n in FAMILY_NAMES if n not in rescued],
        "armas": weapons or ["Garras (inicial)"],
        "puntuacion": player.get("score", 0),
        "tiempo_jugado": f"{seconds // 60}m {seconds % 60}s",
    }


# ----------------------------------------------------------------------
# Tabla de récords
# ----------------------------------------------------------------------

@app.post("/api/score")
async def submit_score(entry: ScoreEntry):
    store = load_data()
    store["scores"].append(entry.model_dump())
    store["scores"].sort(key=lambda s: s["score"], reverse=True)
    store["scores"] = store["scores"][:50]
    save_data(store)
    position = next((i + 1 for i, s in enumerate(store["scores"])
                     if s["score"] == entry.score and s["name"] == entry.name), None)
    return {"status": "ok", "posicion": position}


@app.get("/api/scores")
async def top_scores(limit: int = 10) -> List[dict]:
    store = load_data()
    return [
        {
            "puesto": i + 1,
            "nombre": s["name"],
            "puntuacion": s["score"],
            "barrio": s["level"],
            "dificultad": DIFFICULTY_NAMES.get(s.get("difficulty", 1), "NORMAL"),
        }
        for i, s in enumerate(store["scores"][:max(1, min(limit, 50))])
    ]


@app.get("/api/status")
async def status():
    store = load_data()
    return {
        "servidor": "ÉXODO Core Server",
        "estado": "en línea",
        "partidas_guardadas": len(store["players"]),
        "records_registrados": len(store["scores"]),
    }
