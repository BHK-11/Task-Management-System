package com.taskos.goals.dto;

import jakarta.validation.constraints.Size;

public class ReviewForm {
    @Size(max = 3000) private String reflection = "";
    @Size(max = 2000) private String wins = "";
    @Size(max = 2000) private String challenges = "";
    @Size(max = 2000) private String nextSteps = "";
    public String getReflection() { return reflection; } public void setReflection(String v) { reflection = v; }
    public String getWins() { return wins; } public void setWins(String v) { wins = v; }
    public String getChallenges() { return challenges; } public void setChallenges(String v) { challenges = v; }
    public String getNextSteps() { return nextSteps; } public void setNextSteps(String v) { nextSteps = v; }
}
