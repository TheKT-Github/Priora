export interface GravitySettings {
  id?: number;
  enableDependencyScore: boolean;
  enableDeadlineProximityScore: boolean;
  enableStagnationScore: boolean;
  enableWorkloadDensityScore: boolean;
  requireApprovalForDevTasks: boolean;
  enableCustomFactor?: boolean;
  customFactorName?: string;
  customFactorPoints?: number;
}
