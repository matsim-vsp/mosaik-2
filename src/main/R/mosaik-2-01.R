# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
library(ggnewscale)
library(ncdf4)

cbPalette <- c("#4285f4", "#fbbc04", "#ea4335", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287", "#71c457", "#71c897")

# read in the simulated palm data from the base case. Make sure we have only 24 hours and select the
# raster tile which contains monitoring station 010
# then convert NOx from g into mikro grams
palm_base <- read_csv("/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units.xyt.csv") %>%
  filter(time < 86400) %>% # filter this here, because it is difficult to do with lubridate hms format.
  mutate(time = hms::as_hms(time)) %>%
  select(time, x, y, NOx) %>%
  filter(x > 388056.5 - 1 &
           x < 388056.5 + 1 &
           y > 5822709 - 1 &
           y < 5822709 + 1) %>%
  mutate(NOx = NOx * 1e6) %>%
  mutate(name = "NOx Concentration")

# read in wind speeds for months june - august
# create proper date and time columns
wind_speeds <- read_csv(
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/430_wind_speeds_june-august.csv",
  col_types = cols(Zeitstempel = col_datetime(format = "%Y-%m-%dT%H:%M:%S"))) %>%
  mutate(
    date = as.Date(Zeitstempel),
    time = hms::as_hms(Zeitstempel)
  ) %>%
  rename(wind_speed = Wert) %>%
  select(date, time, wind_speed)

# read in wind directions for months june - august
# same transformations as wind speeds
wind_directions <- read_csv(
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/430_wind_directions_june-august.csv",
  col_types = cols(Zeitstempel = col_datetime(format = "%Y-%m-%dT%H:%M:%S"))
) %>%
  mutate(
    date = as.Date(Zeitstempel),
    time = hms::as_hms(Zeitstempel)
  ) %>%
  rename(wind_direction = Wert) %>%
  select(date, time, wind_direction)

# read in air quality monitoring data for months june - august
# apply proper date and time columns
curb_data_010 <- read_delim(
  "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/monitoring-stations/010_air_quality_june-august.csv",
  delim = ";",
  col_types = list(col_datetime("%d.%m.%Y %H:%M"), col_double(), col_double(), col_double())
) %>%
  mutate(
    date = as.Date(Date),
    time = hms::as_hms(Date)
  ) %>%
  select(date, time, NOx)

chemistry <- nc_open("/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/matsim-output/berlin-with-geometry-attributes.output_emission_raster.nc")
species <- ncvar_get(chemistry, "emission_name")
print(species)
emission_values <- ncvar_get(chemistry, "emission_values")
print(dim(emission_values))

# Define the coordinate ranges
x_range <- 520:530
y_range <- 570:580
species_range <- 1:2
total_hours <- dim(emission_values)[4]
hour_range <- (total_hours - 23):total_hours

# Select the NO and NO2 species, specific coordinate ranges, and the last 24 hours
no_no2_last_24_hours <- emission_values[species_range, y_range, x_range, hour_range]

# Sum NO and NO2 per coordinate (x, y) for each hour
nox_last_24_hours <- apply(no_no2_last_24_hours, c(2, 3, 4), sum)

# Calculate the average NOx over all coordinates (x and y) within the specified ranges for each of the last 24 hours
average_nox_each_hour <- apply(nox_last_24_hours, 3, mean)

time_objects <- hms::as_hms(seq(0, by = 3600, length.out = 24))

nox_scaling_factor <- 5.0e4

# Create a data frame for plotting
data <- tibble(
  hour = time_objects,
  avg_NOx = average_nox_each_hour,
) %>%
  mutate(avg_NOx_scaled = avg_NOx * nox_scaling_factor)

# Plot using ggplot
ggplot(data, aes(x = hour, y = avg_NOx)) +
  geom_line(color = "blue") +
  geom_point(color = "blue") +
  labs(title = "Hourly Average NOx Emissions Over the Last 24 Hours (Filtered Coordinates)",
       x = "Hour",
       y = "Average NOx Emissions") +
  theme_minimal()
# find 5 days with lowest average wind speeds
low_wind_speed_dates <- wind_speeds %>%
  group_by(date) %>%
  summarise(daily_mean = mean(wind_speed)) %>%
  arrange(daily_mean) %>%
  head(5) %>%
  mutate(date_string = format(date, "%b %d"))

# join to get the hourly data of the lowest wind speed days
low_wind_speeds <- left_join(low_wind_speed_dates, wind_speeds, by = join_by(date))

# join to get the hourly concentrations of low wind speed days
curb_data_010_on_low_speed_dates <- left_join(low_wind_speeds, curb_data_010, by = join_by(date, time)) %>%
  mutate(name = "monitoring data") %>%
  select(date_string, time, NOx, name)

# plot the monitored and the simulated NOx concentrations over hours of the day
p <- ggplot(curb_data_010_on_low_speed_dates, aes(x = time, y = NOx)) +

  # Add the inflow of emissions as separate scale
  geom_line(data = data, aes(x = hour, y = avg_NOx_scaled, color = "NOx Inflow"), size =0.75) +
  scale_color_manual(name = "PALM Input", values = "#fbbc04") +
  # add the low wind speed day concentrations

  new_scale_color() +
  geom_line(aes(color = date_string, group = date_string), alpha = 0.9) +
  scale_color_manual(name = "Monitored", values = c("#4285f4", "#3b77db", "#346ac3", "#2e5daa", "#274f92", "#21427a", "#1a3561")) +

  # Add the simulated concentrations as separate scale
  new_scale_color() +
  geom_line(data = palm_base, aes(color = "NOx Concentration"), size = 1.0) +
  scale_color_manual(name = "PALM Output", values = "#ea4335") +

  # add a secondary y-axis for the emission values
  scale_y_continuous(
    name = "Concentration [\u00B5g/m3]",  # Primary y-axis
    sec.axis = sec_axis(~ ./(nox_scaling_factor / 1000), name = "Inflow [mg/m2]", )#breaks = c(0, 0.33, 0.66, 1.0))  # Secondary y-axis
  ) +

  # Formatting
  ggtitle("Monitored and simulated NOx concentrations at station 010 and NOx inflow ") +
  xlab("Time of Day") +
  ylab("Concentration [\u00B5g/m3]") +
  theme_light()
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/r-output/nox-comparison.png", width = 220, height = 118, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-01/data-files-nextcloud/r-output/nox-comparison.pdf", width = 220, height = 118, units = "mm")

# Here we have plots for wind speeds and wind directions for the selected low wind speed days. We don't use them in our
# paper but I used them to verify wind is mainly from western directions and at what times we have which wind speeds.
p <- ggplot(low_wind_speeds, aes(x = time, y = wind_speed)) +
  geom_line(aes(color = date, group = date)) +
  theme_light()
p

wind_directions_on_low_speed_dates <- left_join(low_wind_speed_dates, wind_directions, by = join_by(date)) %>%
  select(date, time, wind_direction)

p <- ggplot(wind_directions_on_low_speed_dates, aes(x = time, y = wind_direction)) +
  geom_line(aes(color = date, group = date)) +
  geom_hline(yintercept = 230, color = "red") +
  geom_hline(yintercept = 310, color = "red") +
  theme_light()
p