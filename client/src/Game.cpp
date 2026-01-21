#include "../include/Game.h"

Game::Game(std::string team_a, std::string team_b)
    : team_a_name(team_a), team_b_name(team_b) {
}

void Game::addEvent(const Event& event) {
    events.push_back(event);
    std::sort(events.begin(), events.end());
    map<string, string> game_updates = event.get_game_updates();
    map<string, string> team_a_updates = event.get_team_a_updates();
    map<string, string> team_b_updates = event.get_team_b_updates();
    for (const auto& genStat : game_updates) {
        general_stats[genStat.first] = genStat.second;
    }
    for (const auto& teamAStat : team_a_updates) {
        team_a_stats[teamAStat.first] = teamAStat.second;
    }
    for (const auto& teamBStat : team_b_updates) {
        team_b_stats[teamBStat.first] = teamBStat.second;
    }

}

vector<Event> Game::getEvents() const {
    return events;
}

const map<string, string>& Game::getGeneralStats() const {
    return general_stats;
}

const map<string, string>& Game::getTeamAStats() const {
    return team_a_stats;
}

const map<string, string>& Game::getTeamBStats() const {
    return team_b_stats;
}