import { can, statusLabel } from '../ui/format'

export const canCreateRequirement = (roles: string[]) => can.manageRequirements(roles)
export const describeStatus = (status: string) => statusLabel(status)
