library(tidyverse)
library(dplyr)
library(broom)

print("starting to read csv")
csv_data <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/linear-fit-with-times.csv")
#csv_data <- head(csv_data, 1000000)
csv_data

csv_data %>%
  group_by(time) %>%
  group_map(~ cor(.x$matsim, .x$palm))

csv_data %>%
  filter(palm < 0.001) %>%
  group_by(time) %>%
  group_map(~ summary(lm(.x$palm ~ .x$matsim, .x)))

for(time in unique(csv_data$time)) {
  print(time)
  filtered <- filter(csv_data, time == time)
  print(cor(filtered$matsim, filtered$palm))
}

plot <- ggplot(data = csv_data, mapping = aes(x = matsim, y = palm)) +
  geom_point(pch='.') +
  geom_smooth() +
  facet_wrap(vars(time))
plot

summary <- summarize(csv_data)
summary

print("starting linear regression")
linear_model <- lm(palm ~ matsim, data = csv_data)
lm_summary <- summary(linear_model)
lm_summary

intercept <- lm_summary$coefficients["(Intercept)", "Estimate"]
grade <- lm_summary$coefficients["matsim", "Estimate"]
intercept
grade

print("starting linear regression with quadratic fitting")
quad_model <- lm(palm ~ poly(matsim,2,raw=TRUE), data=csv_data)
quad_summary <- summary(quad_model)
quad_summary

quad_c  <- quad_model$coefficients["(Intercept)"]
quad_c
quad_b <- quad_model$coefficients["poly(matsim, 2, raw = TRUE)1"]
quad_b
quad_a <- quad_model$coefficients["poly(matsim, 2, raw = TRUE)2"]
quad_a


print("plotting data")
plot <- ggplot(data = csv_data, mapping = aes(x = matsim, y = palm)) +
  geom_point(pch='.') +
  stat_function(fun = function(x) intercept + x * grade, color = "red", size = 1) +
  stat_function(fun = function(x) quad_a * x * x + quad_b * x + quad_c, color = "blue", size = 1)
print("finished plot")

print("saving plot")
ggsave(plot=plot, filename="pm10.png", width=16, height=9)